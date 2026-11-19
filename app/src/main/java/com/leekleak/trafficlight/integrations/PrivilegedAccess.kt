package com.leekleak.trafficlight.integrations

import kotlinx.coroutines.flow.StateFlow

/**
 * Backend that can provide privileged telephony data.
 *
 * The kind is part of the domain so the UI can name the active backend, but no backend
 * implementation type (libsu, binder, Shizuku) may leak through this contract.
 */
enum class PrivilegedBackendKind {
    Root,
    Shizuku,
    Basic,
}

/** Whether a backend could work at all. Availability is never authorization. */
enum class BackendAvailability {
    Available,
    Unavailable,
    Unknown,
}

/** Whether the user granted the backend. Kept separate from [BackendAvailability]. */
enum class BackendAuthorization {
    NotRequired,
    Unknown,
    Required,
    Granted,
    Denied,
    Revoked,
}

/**
 * Failure categories safe to surface. They deliberately carry no message, command,
 * identifier, or binder token so nothing sensitive can reach a log or the UI.
 */
sealed interface PrivilegedAccessError {
    data object NotInstalled : PrivilegedAccessError
    data object AuthorizationDenied : PrivilegedAccessError
    data object AuthorizationRevoked : PrivilegedAccessError
    data object Timeout : PrivilegedAccessError
    data object CommandFailed : PrivilegedAccessError
    data object MalformedOutput : PrivilegedAccessError
    data object BinderUnavailable : PrivilegedAccessError
    data object UnsupportedPlatform : PrivilegedAccessError
    data object Unknown : PrivilegedAccessError
}

sealed interface PrivilegedAccessState {
    data object Disabled : PrivilegedAccessState
    data object Detecting : PrivilegedAccessState
    data class AuthorizationRequired(val kind: PrivilegedBackendKind) : PrivilegedAccessState
    data class Enabling(val kind: PrivilegedBackendKind) : PrivilegedAccessState
    data class Active(val kind: PrivilegedBackendKind) : PrivilegedAccessState
    data object Unavailable : PrivilegedAccessState
    data class Error(
        val kind: PrivilegedBackendKind,
        val reason: PrivilegedAccessError,
        val recoverable: Boolean,
    ) : PrivilegedAccessState
}

/** One subscription as the domain needs it. [subscriberId] must never be logged. */
data class SubscriptionRecord(
    val subscriptionId: Int,
    val simSlotIndex: Int,
    val carrierName: String,
    val subscriberId: String?,
)

data class SubscriptionSnapshot(
    val backend: PrivilegedBackendKind,
    val subscriptions: List<SubscriptionRecord>,
    val capturedAt: Long,
)

/** Explicit result type instead of exceptions so failures stay categorised and log-safe. */
sealed interface PrivilegedAccessOutcome<out T> {
    data class Success<T>(val value: T) : PrivilegedAccessOutcome<T>
    data class Failure(val error: PrivilegedAccessError) : PrivilegedAccessOutcome<Nothing>
}

/**
 * A single privileged backend.
 *
 * Implementations must never request authorization from [availability], [authorization],
 * [enable] or [readSubscriptions]; only [requestAuthorization] may trigger a system prompt.
 */
interface PrivilegedBackend {
    val kind: PrivilegedBackendKind

    suspend fun availability(): BackendAvailability

    suspend fun authorization(): BackendAuthorization

    /** Explicit user action only. */
    suspend fun requestAuthorization(): BackendAuthorization

    suspend fun enable(): PrivilegedAccessOutcome<Unit>

    suspend fun disable()

    suspend fun readSubscriptions(): PrivilegedAccessOutcome<List<SubscriptionRecord>>
}

/**
 * Front door used by ViewModels and managers. Exposes domain state only.
 */
interface PrivilegedAccessProvider {
    val state: StateFlow<PrivilegedAccessState>

    /** User intent: advanced access is wanted. Persisted separately from any grant. */
    val enabled: StateFlow<Boolean>

    /** Re-evaluate backend health without prompting. */
    suspend fun refresh()

    /** Explicit user action that may prompt for root/Shizuku authorization. */
    suspend fun requestAccess(): PrivilegedAccessState

    /** Turn advanced access off and return to the basic backend. */
    suspend fun stop()

    /** Push the current snapshot into DataPlanManager. Never stores a grant. */
    suspend fun syncSubscriptions()
}
