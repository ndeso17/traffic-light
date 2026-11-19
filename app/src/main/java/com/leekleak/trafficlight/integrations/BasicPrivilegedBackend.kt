package com.leekleak.trafficlight.integrations

/**
 * Always-present fallback backend.
 *
 * It needs no authorization and reports no subscriptions, so the coordinator can fall back to
 * basic single-plan tracking without a privileged backend. It exists in every flavor.
 */
class BasicPrivilegedBackend : PrivilegedBackend {
    override val kind: PrivilegedBackendKind = PrivilegedBackendKind.Basic

    override suspend fun availability(): BackendAvailability = BackendAvailability.Available

    override suspend fun authorization(): BackendAuthorization = BackendAuthorization.NotRequired

    override suspend fun requestAuthorization(): BackendAuthorization = BackendAuthorization.NotRequired

    override suspend fun enable(): PrivilegedAccessOutcome<Unit> = PrivilegedAccessOutcome.Success(Unit)

    override suspend fun disable() = Unit

    override suspend fun readSubscriptions(): PrivilegedAccessOutcome<List<SubscriptionRecord>> =
        PrivilegedAccessOutcome.Success(emptyList())
}
