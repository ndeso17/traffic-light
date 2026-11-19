package com.leekleak.trafficlight.integrations

import com.leekleak.trafficlight.database.AppPreferenceRepo
import com.leekleak.trafficlight.model.DataPlanManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Single owner of backend selection: Root → Shizuku → Basic.
 *
 * Rules enforced here:
 * - nothing prompts during construction, refresh, or lifecycle updates,
 * - only [requestAccess] may trigger a system authorization prompt,
 * - transitions are serialized with a [Mutex] and never loop,
 * - a failed backend falls back to the next one and finally to Basic,
 * - the persisted flag is user intent, never a grant.
 */
class PrivilegedAccessProviderImpl(
    backends: List<PrivilegedBackend>,
    private val appPreferenceRepo: AppPreferenceRepo,
    private val dataPlanManager: DataPlanManager,
    private val scope: CoroutineScope,
) : PrivilegedAccessProvider {

    // Enum order is the product priority: Root, Shizuku, Basic.
    private val ordered: List<PrivilegedBackend> = backends.sortedBy { it.kind.ordinal }
    private val privileged: List<PrivilegedBackend> =
        ordered.filter { it.kind != PrivilegedBackendKind.Basic }

    init {
        require(ordered.any { it.kind == PrivilegedBackendKind.Basic }) {
            "Basic backend is required"
        }
    }

    private val mutex = Mutex()

    /** Denials are remembered for this process only; a restart may retry. Never persisted. */
    private val denied = mutableSetOf<PrivilegedBackendKind>()

    private val _state = MutableStateFlow<PrivilegedAccessState>(PrivilegedAccessState.Disabled)
    override val state: StateFlow<PrivilegedAccessState> = _state.asStateFlow()

    override val enabled: StateFlow<Boolean> = appPreferenceRepo.advancedAccessEnabled
        .stateIn(scope, SharingStarted.Eagerly, false)

    init {
        scope.launch {
            appPreferenceRepo.advancedAccessEnabled.collect { wanted ->
                if (!wanted) _state.value = PrivilegedAccessState.Disabled
            }
        }
    }

    override suspend fun refresh() {
        mutex.withLock {
            if (!appPreferenceRepo.advancedAccessEnabled.first()) {
                _state.value = PrivilegedAccessState.Disabled
                return@withLock
            }
            _state.value = PrivilegedAccessState.Detecting
            _state.value = detect()
        }
    }

    override suspend fun requestAccess(): PrivilegedAccessState {
        appPreferenceRepo.setAdvancedAccessEnabled(true)
        return mutex.withLock {
            _state.value = PrivilegedAccessState.Detecting
            var lastError: PrivilegedAccessError? = null

            for (backend in privileged) {
                if (availabilityOf(backend) == BackendAvailability.Unavailable) continue

                _state.value = PrivilegedAccessState.Enabling(backend.kind)
                val authorization = if (authorizationOf(backend) == BackendAuthorization.Granted) {
                    BackendAuthorization.Granted
                } else {
                    runCatching { backend.requestAuthorization() }
                        .getOrDefault(BackendAuthorization.Denied)
                }

                if (authorization != BackendAuthorization.Granted) {
                    denied += backend.kind
                    lastError = PrivilegedAccessError.AuthorizationDenied
                    continue
                }

                when (val outcome = runCatching { backend.enable() }
                    .getOrElse { PrivilegedAccessOutcome.Failure(PrivilegedAccessError.Unknown) }) {
                    is PrivilegedAccessOutcome.Success -> {
                        val error = syncFrom(backend)
                        if (error == null) {
                            denied -= backend.kind
                            _state.value = PrivilegedAccessState.Active(backend.kind)
                            return@withLock _state.value
                        }
                        denied += backend.kind
                        lastError = error
                    }
                    is PrivilegedAccessOutcome.Failure -> {
                        denied += backend.kind
                        lastError = outcome.error
                    }
                }
            }

            val error = lastError
            _state.value = if (error != null) {
                PrivilegedAccessState.Error(PrivilegedBackendKind.Basic, error, recoverable = true)
            } else {
                PrivilegedAccessState.Active(PrivilegedBackendKind.Basic)
            }
            syncBasic()
            _state.value
        }
    }

    override suspend fun stop() {
        mutex.withLock {
            privileged.forEach { backend -> runCatching { backend.disable() } }
            appPreferenceRepo.setAdvancedAccessEnabled(false)
            denied.clear()
            _state.value = PrivilegedAccessState.Disabled
            syncBasic()
        }
    }

    override suspend fun syncSubscriptions() {
        mutex.withLock {
            val kind = (_state.value as? PrivilegedAccessState.Active)?.kind ?: return@withLock
            if (kind == PrivilegedBackendKind.Basic) {
                syncBasic()
                return@withLock
            }

            val active = ordered.firstOrNull { it.kind == kind } ?: return@withLock
            val error = syncFrom(active)
            if (error == null) return@withLock

            // Runtime fallback: try the next granted backend without prompting.
            for (candidate in privileged) {
                if (candidate.kind == kind || candidate.kind in denied) continue
                if (authorizationOf(candidate) != BackendAuthorization.Granted) continue
                val enabledCandidate = runCatching { candidate.enable() }
                    .getOrElse { PrivilegedAccessOutcome.Failure(PrivilegedAccessError.Unknown) }
                if (enabledCandidate is PrivilegedAccessOutcome.Success && syncFrom(candidate) == null) {
                    _state.value = PrivilegedAccessState.Active(candidate.kind)
                    return@withLock
                }
            }

            _state.value = PrivilegedAccessState.Error(kind, error, recoverable = true)
            syncBasic()
        }
    }

    private suspend fun detect(): PrivilegedAccessState {
        var lastError: PrivilegedAccessError? = null

        for (backend in privileged) {
            if (backend.kind in denied) continue
            if (availabilityOf(backend) == BackendAvailability.Unavailable) continue

            when (authorizationOf(backend)) {
                BackendAuthorization.Granted -> {
                    val outcome = runCatching { backend.enable() }
                        .getOrElse { PrivilegedAccessOutcome.Failure(PrivilegedAccessError.Unknown) }
                    if (outcome is PrivilegedAccessOutcome.Success) {
                        val error = syncFrom(backend)
                        if (error == null) {
                            return PrivilegedAccessState.Active(backend.kind)
                        }
                        lastError = error
                    } else {
                        lastError = (outcome as PrivilegedAccessOutcome.Failure).error
                    }
                }
                BackendAuthorization.Required, BackendAuthorization.Unknown ->
                    return PrivilegedAccessState.AuthorizationRequired(backend.kind)
                else -> Unit
            }
        }

        val error = lastError
        return if (error != null) {
            PrivilegedAccessState.Error(PrivilegedBackendKind.Basic, error, recoverable = true)
        } else {
            PrivilegedAccessState.Active(PrivilegedBackendKind.Basic)
        }
    }

    private suspend fun availabilityOf(backend: PrivilegedBackend): BackendAvailability =
        runCatching { backend.availability() }.getOrDefault(BackendAvailability.Unknown)

    private suspend fun authorizationOf(backend: PrivilegedBackend): BackendAuthorization =
        runCatching { backend.authorization() }.getOrDefault(BackendAuthorization.Unknown)

    /** Returns null on success, or the failure category. Never throws. */
    private suspend fun syncFrom(backend: PrivilegedBackend): PrivilegedAccessError? {
        val outcome = runCatching { backend.readSubscriptions() }
            .getOrElse { return PrivilegedAccessError.Unknown }
        return when (outcome) {
            is PrivilegedAccessOutcome.Success -> {
                applyRecords(outcome.value)
                null
            }
            is PrivilegedAccessOutcome.Failure -> outcome.error
        }
    }

    /**
     * Mirrors the existing Shizuku reconciliation: match plans by subscriber id, create the
     * missing ones, then persist the slot indices. Subscriber ids stay in memory only.
     */
    private suspend fun applyRecords(records: List<SubscriptionRecord>) {
        val sorted = records.sortedBy { it.simSlotIndex }
        val activeIds = sorted.map { it.subscriberId }
        val existing = dataPlanManager.getPlans()
        val updated = existing.map { plan -> plan.copy(simIndex = activeIds.indexOf(plan.decryptedID)) }
        val knownIds = updated.map { it.decryptedID }

        sorted.forEach { record ->
            val subscriberId = record.subscriberId
            if (subscriberId != null && subscriberId !in knownIds) {
                dataPlanManager.createPlan(
                    plainSubscriberID = subscriberId,
                    simIndex = record.simSlotIndex,
                    carrierName = record.carrierName,
                )
            }
        }

        dataPlanManager.addPlans(updated)
    }

    private suspend fun syncBasic() {
        runCatching { updateSimDataBasic(dataPlanManager) }
    }
}
