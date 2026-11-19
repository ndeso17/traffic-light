package com.leekleak.trafficlight.integrations

/**
 * Root backend for the Full flavor only.
 *
 * Security boundaries:
 * - commands are constants from [RootCommand.ALLOWLIST]; nothing is interpolated,
 * - [availability] and [authorization] never prompt, they only read an already granted shell,
 * - every command has a deadline and a cancellation path,
 * - exit codes are validated and output is parsed with strict bounds,
 * - no record, identifier, command, or raw output is ever logged.
 */
internal class RootPrivilegedBackend(
    private val runner: RootShellRunner = LibsuRootShellRunner(),
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) : PrivilegedBackend {

    override val kind: PrivilegedBackendKind = PrivilegedBackendKind.Root

    override suspend fun availability(): BackendAvailability {
        // Only an already authorized shell can prove availability without prompting.
        val session = runCatching { runner.cached() }.getOrNull() ?: return BackendAvailability.Unknown
        return if (session.isRoot) BackendAvailability.Available else BackendAvailability.Unavailable
    }

    override suspend fun authorization(): BackendAuthorization {
        val session = runCatching { runner.cached() }.getOrNull() ?: return BackendAuthorization.Unknown
        return if (session.isRoot) BackendAuthorization.Granted else BackendAuthorization.Required
    }

    /** The only entry point that may show a superuser prompt. */
    override suspend fun requestAuthorization(): BackendAuthorization {
        val session = runCatching { runner.acquire() }.getOrNull()
            ?: return BackendAuthorization.Denied
        return if (session.isRoot) BackendAuthorization.Granted else BackendAuthorization.Denied
    }

    override suspend fun enable(): PrivilegedAccessOutcome<Unit> {
        val session = runCatching { runner.cached() }.getOrNull()
            ?: return PrivilegedAccessOutcome.Failure(PrivilegedAccessError.AuthorizationDenied)
        if (!session.isRoot) {
            return PrivilegedAccessOutcome.Failure(PrivilegedAccessError.AuthorizationDenied)
        }

        val probe = session.run(RootCommand.requireAllowlisted(RootCommand.UID), timeoutMs)
        return when {
            probe.timedOut -> PrivilegedAccessOutcome.Failure(PrivilegedAccessError.Timeout)
            probe.exitCode != 0 -> PrivilegedAccessOutcome.Failure(PrivilegedAccessError.CommandFailed)
            !RootSubscriptionParser.parseUid(probe.stdout) ->
                PrivilegedAccessOutcome.Failure(PrivilegedAccessError.MalformedOutput)
            else -> PrivilegedAccessOutcome.Success(Unit)
        }
    }

    override suspend fun disable() {
        runner.closeCached()
    }

    override suspend fun readSubscriptions(): PrivilegedAccessOutcome<List<SubscriptionRecord>> {
        val session = runCatching { runner.cached() }.getOrNull()
            ?: return PrivilegedAccessOutcome.Failure(PrivilegedAccessError.AuthorizationDenied)
        if (!session.isRoot) {
            return PrivilegedAccessOutcome.Failure(PrivilegedAccessError.AuthorizationRevoked)
        }

        val probe = session.run(
            RootCommand.requireAllowlisted(RootCommand.SUBSCRIPTIONS),
            timeoutMs,
        )
        if (probe.timedOut) {
            return PrivilegedAccessOutcome.Failure(PrivilegedAccessError.Timeout)
        }
        if (probe.exitCode != 0) {
            return PrivilegedAccessOutcome.Failure(PrivilegedAccessError.CommandFailed)
        }

        val records = RootSubscriptionParser.parseSubscriptions(probe.stdout)
        return if (records.isEmpty()) {
            // An empty provider result means the data could not be read, not that the device
            // has no SIMs, so it is reported as a failure and the coordinator falls back.
            PrivilegedAccessOutcome.Failure(PrivilegedAccessError.MalformedOutput)
        } else {
            PrivilegedAccessOutcome.Success(records)
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 8_000L
    }
}

/** Constant command allowlist. No user input is ever interpolated. */
internal object RootCommand {
    const val UID = "id -u"
    const val SUBSCRIPTIONS =
        "content query --uri content://telephony/siminfo --projection _id:sim_id:carrier_name:imsi"

    val ALLOWLIST: List<String> = listOf(UID, SUBSCRIPTIONS)

    fun requireAllowlisted(command: String): String = command.also {
        require(it in ALLOWLIST) { "Command is not allowlisted" }
    }
}
