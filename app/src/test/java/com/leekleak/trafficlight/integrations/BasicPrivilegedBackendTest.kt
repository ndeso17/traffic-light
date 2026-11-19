package com.leekleak.trafficlight.integrations

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BasicPrivilegedBackendTest {
    private val backend = BasicPrivilegedBackend()

    @Test
    fun `basic is always available and needs no authorization`() = runTest {
        assertEquals(PrivilegedBackendKind.Basic, backend.kind)
        assertEquals(BackendAvailability.Available, backend.availability())
        assertEquals(BackendAuthorization.NotRequired, backend.authorization())
        assertEquals(BackendAuthorization.NotRequired, backend.requestAuthorization())
    }

    @Test
    fun `basic returns an empty safe snapshot`() = runTest {
        assertTrue(backend.enable() is PrivilegedAccessOutcome.Success)
        val result = backend.readSubscriptions()
        assertTrue(result is PrivilegedAccessOutcome.Success)
        assertTrue((result as PrivilegedAccessOutcome.Success).value.isEmpty())
    }
}
