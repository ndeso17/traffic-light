package com.leekleak.trafficlight.integrations

/**
 * Lite flavor has no Shizuku integration, so this backend only reports that it is not
 * available. It keeps the coordinator flavor-agnostic without shipping Shizuku code.
 */
class ShizukuPrivilegedBackend : PrivilegedBackend {
    override val kind: PrivilegedBackendKind = PrivilegedBackendKind.Shizuku

    override suspend fun availability(): BackendAvailability = BackendAvailability.Unavailable

    override suspend fun authorization(): BackendAuthorization = BackendAuthorization.Unknown

    override suspend fun requestAuthorization(): BackendAuthorization = BackendAuthorization.Denied

    override suspend fun enable(): PrivilegedAccessOutcome<Unit> =
        PrivilegedAccessOutcome.Failure(PrivilegedAccessError.NotInstalled)

    override suspend fun disable() = Unit

    override suspend fun readSubscriptions(): PrivilegedAccessOutcome<List<SubscriptionRecord>> =
        PrivilegedAccessOutcome.Failure(PrivilegedAccessError.NotInstalled)
}
