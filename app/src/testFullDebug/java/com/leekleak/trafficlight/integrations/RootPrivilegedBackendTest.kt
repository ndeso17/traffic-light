package com.leekleak.trafficlight.integrations

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootPrivilegedBackendTest {

    private class FakeSession(
        override val isRoot: Boolean,
        private val results: Map<String, RootCommandResult> = emptyMap(),
        private val default: RootCommandResult = RootCommandResult(0, emptyList(), emptyList(), false),
    ) : RootShellSession {
        val commands = mutableListOf<String>()

        override suspend fun run(command: String, timeoutMs: Long): RootCommandResult {
            commands += command
            return results[command] ?: default
        }
    }

    private class FakeRunner(
        var cachedSession: RootShellSession? = null,
        var acquiredSession: RootShellSession = FakeSession(isRoot = false),
    ) : RootShellRunner {
        var acquireCount = 0
            private set
        var closeCount = 0
            private set

        override suspend fun acquire(): RootShellSession {
            acquireCount++
            return acquiredSession
        }

        override suspend fun cached(): RootShellSession? = cachedSession

        override fun closeCached() {
            closeCount++
        }
    }

    private val providerOutput = listOf(
        "Row: 0 _id=1, sim_id=0, carrier_name=Carrier A, imsi=510000000000001",
        "Row: 1 _id=2, sim_id=1, carrier_name=Carrier B, imsi=510000000000002",
    )

    @Test
    fun `availability and authorization never prompt`() = runTest {
        val runner = FakeRunner(cachedSession = FakeSession(isRoot = true))
        val backend = RootPrivilegedBackend(runner)

        assertEquals(BackendAvailability.Available, backend.availability())
        assertEquals(BackendAuthorization.Granted, backend.authorization())

        assertEquals("no superuser prompt may be triggered", 0, runner.acquireCount)
    }

    @Test
    fun `missing cached shell reports unknown instead of prompting`() = runTest {
        val runner = FakeRunner(cachedSession = null)
        val backend = RootPrivilegedBackend(runner)

        assertEquals(BackendAvailability.Unknown, backend.availability())
        assertEquals(BackendAuthorization.Unknown, backend.authorization())
        assertEquals(0, runner.acquireCount)
    }

    @Test
    fun `enable fails when the shell is not root`() = runTest {
        val runner = FakeRunner(cachedSession = FakeSession(isRoot = false))
        val backend = RootPrivilegedBackend(runner)

        val outcome = backend.enable()

        assertTrue(outcome is PrivilegedAccessOutcome.Failure)
        assertEquals(
            PrivilegedAccessError.AuthorizationDenied,
            (outcome as PrivilegedAccessOutcome.Failure).error,
        )
        assertEquals(0, runner.acquireCount)
    }

    @Test
    fun `enable validates uid output`() = runTest {
        val session = FakeSession(
            isRoot = true,
            results = mapOf(RootCommand.UID to RootCommandResult(0, listOf("0"), emptyList(), false)),
        )
        val backend = RootPrivilegedBackend(FakeRunner(cachedSession = session))

        assertTrue(backend.enable() is PrivilegedAccessOutcome.Success)
    }

    @Test
    fun `enable rejects a non zero exit code`() = runTest {
        val session = FakeSession(
            isRoot = true,
            results = mapOf(RootCommand.UID to RootCommandResult(1, emptyList(), emptyList(), false)),
        )
        val backend = RootPrivilegedBackend(FakeRunner(cachedSession = session))

        val outcome = backend.enable()
        assertEquals(
            PrivilegedAccessError.CommandFailed,
            (outcome as PrivilegedAccessOutcome.Failure).error,
        )
    }

    @Test
    fun `enable reports timeout separately from command failure`() = runTest {
        val session = FakeSession(
            isRoot = true,
            results = mapOf(RootCommand.UID to RootCommandResult(-1, emptyList(), emptyList(), true)),
        )
        val backend = RootPrivilegedBackend(FakeRunner(cachedSession = session))

        val outcome = backend.enable()
        assertEquals(
            PrivilegedAccessError.Timeout,
            (outcome as PrivilegedAccessOutcome.Failure).error,
        )
    }

    @Test
    fun `enable rejects unparseable uid output`() = runTest {
        val session = FakeSession(
            isRoot = true,
            results = mapOf(RootCommand.UID to RootCommandResult(0, listOf("not-a-uid"), emptyList(), false)),
        )
        val backend = RootPrivilegedBackend(FakeRunner(cachedSession = session))

        val outcome = backend.enable()
        assertEquals(
            PrivilegedAccessError.MalformedOutput,
            (outcome as PrivilegedAccessOutcome.Failure).error,
        )
    }

    @Test
    fun `read subscriptions returns the four domain fields`() = runTest {
        val session = FakeSession(
            isRoot = true,
            results = mapOf(
                RootCommand.SUBSCRIPTIONS to RootCommandResult(0, providerOutput, emptyList(), false),
            ),
        )
        val backend = RootPrivilegedBackend(FakeRunner(cachedSession = session))

        val outcome = backend.readSubscriptions()

        assertTrue(outcome is PrivilegedAccessOutcome.Success)
        val records = (outcome as PrivilegedAccessOutcome.Success).value
        assertEquals(2, records.size)
        assertEquals(1, records[0].subscriptionId)
        assertEquals(0, records[0].simSlotIndex)
        assertEquals("Carrier A", records[0].carrierName)
        assertEquals("510000000000001", records[0].subscriberId)
        assertEquals(1, records[1].simSlotIndex)
    }

    @Test
    fun `read subscriptions reports revoked when the cached shell lost root`() = runTest {
        val backend = RootPrivilegedBackend(FakeRunner(cachedSession = FakeSession(isRoot = false)))

        val outcome = backend.readSubscriptions()

        assertEquals(
            PrivilegedAccessError.AuthorizationRevoked,
            (outcome as PrivilegedAccessOutcome.Failure).error,
        )
    }

    @Test
    fun `read subscriptions never runs without an authorized shell`() = runTest {
        val backend = RootPrivilegedBackend(FakeRunner(cachedSession = null))

        val outcome = backend.readSubscriptions()

        assertEquals(
            PrivilegedAccessError.AuthorizationDenied,
            (outcome as PrivilegedAccessOutcome.Failure).error,
        )
    }

    @Test
    fun `empty provider output is a failure so the coordinator can fall back`() = runTest {
        val session = FakeSession(
            isRoot = true,
            results = mapOf(
                RootCommand.SUBSCRIPTIONS to RootCommandResult(0, emptyList(), emptyList(), false),
            ),
        )
        val backend = RootPrivilegedBackend(FakeRunner(cachedSession = session))

        val outcome = backend.readSubscriptions()
        assertEquals(
            PrivilegedAccessError.MalformedOutput,
            (outcome as PrivilegedAccessOutcome.Failure).error,
        )
    }

    @Test
    fun `request authorization is the only path that may prompt`() = runTest {
        val runner = FakeRunner(acquiredSession = FakeSession(isRoot = true))
        val backend = RootPrivilegedBackend(runner)

        assertEquals(BackendAuthorization.Granted, backend.requestAuthorization())
        assertEquals(1, runner.acquireCount)
    }

    @Test
    fun `request authorization reports denial without throwing`() = runTest {
        val runner = FakeRunner(acquiredSession = FakeSession(isRoot = false))
        val backend = RootPrivilegedBackend(runner)

        assertEquals(BackendAuthorization.Denied, backend.requestAuthorization())
    }

    @Test
    fun `disable closes the cached shell`() = runTest {
        val runner = FakeRunner(cachedSession = FakeSession(isRoot = true))
        val backend = RootPrivilegedBackend(runner)

        backend.disable()

        assertEquals(1, runner.closeCount)
    }

    @Test
    fun `allowlist rejects anything that is not a constant command`() {
        assertEquals(RootCommand.UID, RootCommand.requireAllowlisted(RootCommand.UID))
        assertEquals(
            RootCommand.SUBSCRIPTIONS,
            RootCommand.requireAllowlisted(RootCommand.SUBSCRIPTIONS),
        )

        val rejected = runCatching { RootCommand.requireAllowlisted("id -u; rm -rf /") }
        assertTrue(rejected.isFailure)

        val injected = runCatching {
            RootCommand.requireAllowlisted("content query --uri $1")
        }
        assertTrue(injected.isFailure)
    }

    @Test
    fun `allowlist holds no user supplied placeholder`() {
        RootCommand.ALLOWLIST.forEach { command ->
            assertFalse("allowlist entry looks interpolated: $command", command.contains('$'))
            assertFalse("allowlist entry looks interpolated: $command", command.contains('%'))
        }
    }

    @Test
    fun `parser bounds records and line length`() {
        val manyRows = (0 until 50).map {
            "Row: $it _id=$it, sim_id=$it, carrier_name=Carrier $it, imsi=5100000000000$it"
        }
        val oversized = "Row: 99 _id=99, sim_id=99, carrier_name=" + "x".repeat(5_000)

        val records = RootSubscriptionParser.parseSubscriptions(manyRows + oversized)

        assertTrue(records.size <= RootSubscriptionParser.MAX_RECORDS)
        records.forEach { record ->
            assertTrue(record.carrierName.length <= 64)
            assertTrue(record.simSlotIndex >= 0)
        }
    }

    @Test
    fun `parser treats a null imsi as absent instead of a value`() {
        val records = RootSubscriptionParser.parseSubscriptions(
            listOf("Row: 0 _id=1, sim_id=0, carrier_name=Carrier, imsi=null"),
        )

        assertEquals(1, records.size)
        assertEquals(null, records[0].subscriberId)
    }

    @Test
    fun `parser drops rows without a slot index`() {
        val records = RootSubscriptionParser.parseSubscriptions(
            listOf("Row: 0 _id=1, carrier_name=Carrier, imsi=510000000000001"),
        )

        assertTrue(records.isEmpty())
    }
}
