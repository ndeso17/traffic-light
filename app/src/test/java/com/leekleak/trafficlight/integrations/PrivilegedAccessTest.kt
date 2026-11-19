package com.leekleak.trafficlight.integrations

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contract has to keep three ideas apart:
 * 1. the backend exists and could work (availability),
 * 2. the user granted it (authorization),
 * 3. advanced access is what the user wants (intent, owned by the provider).
 *
 * Conflating them is what made `shizuku_tracking` act as a fake grant, so each pair is
 * asserted here rather than left to convention.
 */
class PrivilegedAccessTest {

    private class FakeBackend(
        override val kind: PrivilegedBackendKind,
        var availability: BackendAvailability = BackendAvailability.Available,
        var authorization: BackendAuthorization = BackendAuthorization.Required,
        var requestResult: BackendAuthorization = BackendAuthorization.Granted,
        var records: PrivilegedAccessOutcome<List<SubscriptionRecord>> =
            PrivilegedAccessOutcome.Success(emptyList()),
    ) : PrivilegedBackend {
        var requestCount = 0
            private set

        override suspend fun availability(): BackendAvailability = availability

        override suspend fun authorization(): BackendAuthorization = authorization

        override suspend fun requestAuthorization(): BackendAuthorization {
            requestCount++
            authorization = requestResult
            return requestResult
        }

        override suspend fun enable(): PrivilegedAccessOutcome<Unit> =
            if (authorization in setOf(
                    BackendAuthorization.Granted,
                    BackendAuthorization.NotRequired,
                )
            ) {
                PrivilegedAccessOutcome.Success(Unit)
            } else {
                PrivilegedAccessOutcome.Failure(PrivilegedAccessError.AuthorizationDenied)
            }

        override suspend fun disable() = Unit

        override suspend fun readSubscriptions() = records
    }

    @Test
    fun `available backend is not automatically authorized`() = runTest {
        val backend = FakeBackend(
            kind = PrivilegedBackendKind.Root,
            availability = BackendAvailability.Available,
            authorization = BackendAuthorization.Required,
        )

        assertEquals(BackendAvailability.Available, backend.availability())
        assertEquals(BackendAuthorization.Required, backend.authorization())

        // Availability alone must not be enough to enable the backend.
        val enabled = backend.enable()
        assertTrue(enabled is PrivilegedAccessOutcome.Failure)
        assertEquals(
            PrivilegedAccessError.AuthorizationDenied,
            (enabled as PrivilegedAccessOutcome.Failure).error,
        )
    }

    @Test
    fun `availability probes never request authorization`() = runTest {
        val backend = FakeBackend(kind = PrivilegedBackendKind.Root)

        backend.availability()
        backend.authorization()
        backend.readSubscriptions()

        assertEquals(0, backend.requestCount)
    }

    @Test
    fun `authorization is requested only through the explicit entry point`() = runTest {
        val backend = FakeBackend(kind = PrivilegedBackendKind.Root)

        assertEquals(BackendAuthorization.Required, backend.authorization())
        assertEquals(0, backend.requestCount)

        assertEquals(BackendAuthorization.Granted, backend.requestAuthorization())
        assertEquals(1, backend.requestCount)
        assertTrue(backend.enable() is PrivilegedAccessOutcome.Success)
    }

    @Test
    fun `basic backend needs no authorization`() = runTest {
        val backend = FakeBackend(
            kind = PrivilegedBackendKind.Basic,
            availability = BackendAvailability.Available,
            authorization = BackendAuthorization.NotRequired,
        )

        assertEquals(BackendAuthorization.NotRequired, backend.authorization())
        assertTrue(backend.enable() is PrivilegedAccessOutcome.Success)
        assertEquals(0, backend.requestCount)
    }

    @Test
    fun `unavailable backend reports availability without claiming a grant`() = runTest {
        val backend = FakeBackend(
            kind = PrivilegedBackendKind.Shizuku,
            availability = BackendAvailability.Unavailable,
            authorization = BackendAuthorization.Unknown,
        )

        assertEquals(BackendAvailability.Unavailable, backend.availability())
        assertNotEquals(BackendAuthorization.Granted, backend.authorization())
    }

    @Test
    fun `state exposes backend kind without leaking implementation types`() {
        val active = PrivilegedAccessState.Active(PrivilegedBackendKind.Shizuku)
        assertEquals(PrivilegedBackendKind.Shizuku, active.kind)

        val denied = PrivilegedAccessState.Error(
            kind = PrivilegedBackendKind.Root,
            reason = PrivilegedAccessError.AuthorizationDenied,
            recoverable = true,
        )
        assertEquals(PrivilegedBackendKind.Root, denied.kind)
        assertTrue(denied.recoverable)

        val required = PrivilegedAccessState.AuthorizationRequired(PrivilegedBackendKind.Root)
        assertNotEquals(PrivilegedAccessState.Disabled, required)
    }

    @Test
    fun `error categories carry no sensitive payload`() {
        val errors = listOf(
            PrivilegedAccessError.NotInstalled,
            PrivilegedAccessError.AuthorizationDenied,
            PrivilegedAccessError.AuthorizationRevoked,
            PrivilegedAccessError.Timeout,
            PrivilegedAccessError.CommandFailed,
            PrivilegedAccessError.MalformedOutput,
            PrivilegedAccessError.BinderUnavailable,
            PrivilegedAccessError.UnsupportedPlatform,
            PrivilegedAccessError.Unknown,
        )

        assertEquals(errors.size, errors.toSet().size)
        errors.forEach { error ->
            // Kotlin object instances have only the compiler-generated INSTANCE singleton; no
            // String/Throwable/record payload can be smuggled into a log or UI error.
            val payloadFields = error::class.java.declaredFields
                .filterNot { field -> field.name == "INSTANCE" }
            assertTrue("unexpected payload on $error", payloadFields.isEmpty())
        }
    }

    @Test
    fun `subscriber id is optional but subscription identity is required`() {
        val record = SubscriptionRecord(
            subscriptionId = 1,
            simSlotIndex = 0,
            carrierName = "Carrier",
            subscriberId = null,
        )
        assertEquals(1, record.subscriptionId)
        assertFalse(record.subscriberId?.isNotEmpty() ?: false)

        val snapshot = SubscriptionSnapshot(
            backend = PrivilegedBackendKind.Root,
            subscriptions = listOf(record),
            capturedAt = 1_700_000_000_000L,
        )
        assertEquals(PrivilegedBackendKind.Root, snapshot.backend)
        assertEquals(1, snapshot.subscriptions.size)
    }

    @Test
    fun `failure outcome never carries a value`() {
        val failure: PrivilegedAccessOutcome<List<SubscriptionRecord>> =
            PrivilegedAccessOutcome.Failure(PrivilegedAccessError.Timeout)

        assertTrue(failure is PrivilegedAccessOutcome.Failure)
        assertEquals(
            PrivilegedAccessError.Timeout,
            (failure as PrivilegedAccessOutcome.Failure).error,
        )
    }
}
