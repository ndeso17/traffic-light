package com.leekleak.trafficlight.integrations

import android.content.pm.PackageManager.PERMISSION_GRANTED
import kotlinx.coroutines.delay

/**
 * Shizuku backend for the Full flavor.
 *
 * The existing binder lifecycle is reused unchanged ([ShizukuServicesProviderImpl.enable],
 * [ShizukuServicesProviderImpl.disable]); this class only adapts it to the domain contract so
 * the UI never sees a binder or a Shizuku-specific flag.
 */
class ShizukuPrivilegedBackend(
    private val provider: ShizukuServicesProviderImpl,
    private val permissionPollAttempts: Int = PERMISSION_POLL_ATTEMPTS,
    private val permissionPollMs: Long = PERMISSION_POLL_MS,
) : PrivilegedBackend {

    override val kind: PrivilegedBackendKind = PrivilegedBackendKind.Shizuku

    override suspend fun availability(): BackendAvailability =
        if (runCatching { provider.shizukuRunning() }.getOrDefault(false)) {
            BackendAvailability.Available
        } else {
            BackendAvailability.Unavailable
        }

    override suspend fun authorization(): BackendAuthorization {
        if (!runCatching { provider.shizukuRunning() }.getOrDefault(false)) {
            return BackendAuthorization.Unknown
        }
        return if (runCatching { provider.shizukuPermission() }.getOrDefault(-1) == PERMISSION_GRANTED) {
            BackendAuthorization.Granted
        } else {
            BackendAuthorization.Required
        }
    }

    /** Shizuku answers the permission prompt through a callback, so the grant is polled briefly. */
    override suspend fun requestAuthorization(): BackendAuthorization {
        runCatching { provider.shizukuRequestPermission() }
        repeat(permissionPollAttempts) {
            if (authorization() == BackendAuthorization.Granted) {
                return BackendAuthorization.Granted
            }
            delay(permissionPollMs)
        }
        return BackendAuthorization.Denied
    }

    override suspend fun enable(): PrivilegedAccessOutcome<Unit> {
        if (!runCatching { provider.shizukuRunning() }.getOrDefault(false)) {
            return PrivilegedAccessOutcome.Failure(PrivilegedAccessError.BinderUnavailable)
        }
        if (runCatching { provider.shizukuPermission() }.getOrDefault(-1) != PERMISSION_GRANTED) {
            return PrivilegedAccessOutcome.Failure(PrivilegedAccessError.AuthorizationDenied)
        }
        return if (runCatching { provider.enable() }.getOrDefault(false)) {
            PrivilegedAccessOutcome.Success(Unit)
        } else {
            PrivilegedAccessOutcome.Failure(PrivilegedAccessError.BinderUnavailable)
        }
    }

    override suspend fun disable() {
        runCatching { provider.disable() }
    }

    override suspend fun readSubscriptions(): PrivilegedAccessOutcome<List<SubscriptionRecord>> {
        if (!runCatching { provider.shizukuRunning() }.getOrDefault(false)) {
            return PrivilegedAccessOutcome.Failure(PrivilegedAccessError.BinderUnavailable)
        }

        val infos = runCatching { provider.getSubscriptionInfos() }.getOrElse {
            return PrivilegedAccessOutcome.Failure(PrivilegedAccessError.BinderUnavailable)
        }
        if (infos.isEmpty()) {
            return PrivilegedAccessOutcome.Failure(PrivilegedAccessError.MalformedOutput)
        }

        val records = infos
            .sortedBy { it.simSlotIndex }
            .map { info ->
                SubscriptionRecord(
                    subscriptionId = info.subscriptionId,
                    simSlotIndex = info.simSlotIndex,
                    carrierName = info.carrierName?.toString().orEmpty(),
                    subscriberId = runCatching { provider.getSubscriberID(info.subscriptionId) }
                        .getOrNull(),
                )
            }

        return PrivilegedAccessOutcome.Success(records)
    }

    companion object {
        const val PERMISSION_POLL_ATTEMPTS = 10
        const val PERMISSION_POLL_MS = 300L
    }
}
