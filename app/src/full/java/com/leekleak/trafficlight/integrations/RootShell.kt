package com.leekleak.trafficlight.integrations

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Result of one allowlisted command, already bounded so callers cannot see unbounded output. */
internal data class RootCommandResult(
    val exitCode: Int,
    val stdout: List<String>,
    val stderr: List<String>,
    val timedOut: Boolean,
)

internal interface RootShellSession {
    val isRoot: Boolean
    suspend fun run(command: String, timeoutMs: Long): RootCommandResult
}

/**
 * Narrow seam over libsu so the backend logic can be tested without a real root shell.
 *
 * [acquire] is the only operation that may show a superuser prompt. Everything else must
 * work from an already authorized shell, because availability must never trigger a prompt.
 */
internal interface RootShellRunner {
    suspend fun acquire(): RootShellSession
    suspend fun cached(): RootShellSession?
    fun closeCached()
}

internal class LibsuRootShellRunner : RootShellRunner {
    override suspend fun acquire(): RootShellSession = withContext(Dispatchers.IO) {
        LibsuRootShellSession(Shell.getShell())
    }

    override suspend fun cached(): RootShellSession? = withContext(Dispatchers.IO) {
        Shell.getCachedShell()?.let(::LibsuRootShellSession)
    }

    override fun closeCached() {
        runCatching { Shell.getCachedShell()?.close() }
    }
}

private class LibsuRootShellSession(
    private val shell: Shell,
) : RootShellSession {
    override val isRoot: Boolean get() = shell.isRoot

    override suspend fun run(command: String, timeoutMs: Long): RootCommandResult =
        withContext(Dispatchers.IO) {
            val stdout = mutableListOf<String>()
            val stderr = mutableListOf<String>()
            // libsu 6 drops all output unless the sinks are registered before the job runs.
            val job = shell.newJob().add(command).to(stdout, stderr)
            val future = job.enqueue()
            try {
                val result = future.get(timeoutMs, TimeUnit.MILLISECONDS)
                RootCommandResult(
                    exitCode = result.code,
                    stdout = result.out.toList(),
                    stderr = result.err.toList(),
                    timedOut = false,
                )
            } catch (_: TimeoutException) {
                future.cancel(true)
                // A blocking shell job cannot be interrupted cooperatively, so the shell is
                // closed to actually end it. The next attempt re-acquires a fresh shell.
                runCatching { shell.close() }
                RootCommandResult(exitCode = -1, stdout = emptyList(), stderr = emptyList(), timedOut = true)
            } catch (cancellation: CancellationException) {
                future.cancel(true)
                throw cancellation
            } catch (_: Exception) {
                RootCommandResult(exitCode = -1, stdout = emptyList(), stderr = emptyList(), timedOut = false)
            }
        }
}
