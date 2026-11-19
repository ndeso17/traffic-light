package com.leekleak.trafficlight.integrations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegedAccessStateTest {
    @Test
    fun `root shizuku basic enum order encodes required priority`() {
        assertEquals(
            listOf(
                PrivilegedBackendKind.Root,
                PrivilegedBackendKind.Shizuku,
                PrivilegedBackendKind.Basic,
            ),
            PrivilegedBackendKind.entries,
        )
    }

    @Test
    fun `recoverability is explicit and independent from backend`() {
        val recoverable = PrivilegedAccessState.Error(
            PrivilegedBackendKind.Root,
            PrivilegedAccessError.Timeout,
            recoverable = true,
        )
        val terminal = PrivilegedAccessState.Error(
            PrivilegedBackendKind.Shizuku,
            PrivilegedAccessError.UnsupportedPlatform,
            recoverable = false,
        )

        assertTrue(recoverable.recoverable)
        assertFalse(terminal.recoverable)
    }

    @Test
    fun `active state identifies backend without implementation details`() {
        PrivilegedBackendKind.entries.forEach { kind ->
            assertEquals(kind, PrivilegedAccessState.Active(kind).kind)
        }
    }
}
