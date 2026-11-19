package com.leekleak.trafficlight.spike

import android.app.Activity
import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.topjohnwu.superuser.Shell
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Full-debug-only device harness for the root telephony spike.
 *
 * No shell is created while the activity starts. The only call that can make the root
 * manager show a superuser prompt is [executeSpike], and it only runs after the user
 * pressed the button.
 */
class RootSpikeActivity : Activity() {
    private val commandExecutor = Executors.newSingleThreadExecutor()
    private val timedProbeExecutor = Executors.newSingleThreadExecutor()
    private val waitExecutor = Executors.newSingleThreadExecutor()
    private var activeShell: Shell? = null
    private var attempt = 0
    private lateinit var status: TextView
    private lateinit var runButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(Shell.Builder.create().setTimeout(SHELL_START_TIMEOUT_SECONDS))
        setContentView(createContent())
        status.text = buildString {
            appendLine("No root request has been made.")
            appendLine("KernelSU Next default-unmount is supported: availability is not detected from a su path.")
            appendLine("Press the button to explicitly request authorization and run read-only probes.")
        }
    }

    private fun createContent(): ScrollView {
        status = TextView(this).apply {
            textSize = 16f
            setTextIsSelectable(true)
        }
        runButton = Button(this).apply {
            text = "Request root and run spike"
            setOnClickListener { runAfterExplicitAction() }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            addView(
                TextView(this@RootSpikeActivity).apply {
                    text = "Traffic Light Root Telephony Spike"
                    textSize = 22f
                },
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            addView(runButton, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(status, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        return ScrollView(this).apply { addView(content) }
    }

    private fun runAfterExplicitAction() {
        runButton.isEnabled = false
        attempt += 1
        status.text = "Explicit action recorded (attempt $attempt). Waiting for the root-manager decision…"
        val future = commandExecutor.submit(Callable { executeSpike() })
        waitExecutor.execute {
            val evidence = try {
                future.get(SPIKE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (_: TimeoutException) {
                future.cancel(true)
                activeShell?.close()
                failureEvidence("timeout")
            } catch (_: Exception) {
                failureEvidence("internal_failure")
            }
            val destination = try {
                writeEvidence(evidence)
            } catch (failure: Exception) {
                showResult("Attempt $attempt finished but evidence could not be written: ${failure.javaClass.simpleName}")
                return@execute
            }
            showResult("Attempt $attempt finished. Safe evidence written to:\n$destination\n\n$evidence")
        }
    }

    private fun executeSpike(): String {
        val shellBefore = Shell.getCachedShell()
        val shell = try {
            Shell.getShell().also { activeShell = it }
        } catch (_: Exception) {
            return RootSpikeEvidence.format(
                attempt = attempt,
                device = deviceInfo(
                    selinux = "unknown",
                    grantState = "shell_unavailable",
                    shellStatus = "unavailable",
                    shellStatusInt = Shell.UNKNOWN,
                    shellCachedBefore = shellBefore != null,
                ),
                probes = emptyList(),
                recordSource = "none",
                records = emptyList(),
                providerRecordCount = 0,
                isubRecordCount = 0,
            )
        }

        if (!shell.isRoot) {
            val nonRootStatus = shell.status
            shell.close()
            activeShell = null
            return RootSpikeEvidence.format(
                attempt = attempt,
                device = deviceInfo(
                    selinux = "unknown",
                    grantState = "denied_or_unavailable",
                    shellStatus = "non_root",
                    shellStatusInt = nonRootStatus,
                    shellCachedBefore = shellBefore != null,
                ),
                probes = emptyList(),
                recordSource = "none",
                records = emptyList(),
                providerRecordCount = 0,
                isubRecordCount = 0,
            )
        }

        val uid = runProbe(shell, "uid", RootSpikeProtocol.ALLOWLIST[0])
        if (!RootSpikeProtocol.parseUid(uid.output)) {
            return RootSpikeEvidence.format(
                attempt = attempt,
                device = deviceInfo(
                    selinux = "unknown",
                    grantState = "granted",
                    shellStatus = "root_uid_not_zero",
                    shellStatusInt = shell.status,
                    shellCachedBefore = shellBefore != null,
                ),
                probes = listOf(uid.summary(0, "malformed")),
                recordSource = "none",
                records = emptyList(),
                providerRecordCount = 0,
                isubRecordCount = 0,
            )
        }

        val selinux = runProbe(shell, "selinux", RootSpikeProtocol.ALLOWLIST[1])
        val provider = runProbe(shell, "provider", RootSpikeProtocol.ALLOWLIST[2])
        val isub = runProbe(shell, "isub", RootSpikeProtocol.ALLOWLIST[3])
        val providerRecords = if (provider.exitCode == 0) {
            RootSpikeProtocol.parseContentQuery(provider.output)
        } else {
            emptyList()
        }
        val isubRecords = if (isub.exitCode == 0) {
            RootSpikeProtocol.parseIsubDump(isub.output)
        } else {
            emptyList()
        }
        val (source, records) = RootSpikeProtocol.selectRecords(providerRecords, isubRecords)

        val failureProbe = runProbe(shell, "failure_injection", RootSpikeProtocol.FAILURE_INJECTION[0])
        val timeoutProbe = runTimedProbe(
            shell = shell,
            id = "timeout_injection",
            command = RootSpikeProtocol.FAILURE_INJECTION[1],
            timeoutSeconds = INJECTED_TIMEOUT_SECONDS,
        )

        return RootSpikeEvidence.format(
            attempt = attempt,
            device = deviceInfo(
                selinux = RootSpikeProtocol.parseSelinux(selinux.output),
                grantState = "granted",
                shellStatus = "root_uid_0",
                shellStatusInt = shell.status,
                shellCachedBefore = shellBefore != null,
            ),
            probes = listOf(
                uid.summary(0, categoryOf(uid)),
                selinux.summary(0, categoryOf(selinux)),
                provider.summary(providerRecords.size, categoryOf(provider, providerRecords.isEmpty())),
                isub.summary(isubRecords.size, categoryOf(isub, isubRecords.isEmpty())),
                failureProbe.summary(0, categoryOf(failureProbe)),
                timeoutProbe.summary(0, categoryOf(timeoutProbe)),
            ),
            recordSource = source,
            records = records,
            providerRecordCount = providerRecords.size,
            isubRecordCount = isubRecords.size,
        )
    }

    private fun runProbe(shell: Shell, id: String, command: String): RawProbe {
        val safeCommand = RootSpikeProtocol.requireAllowlisted(command)
        val result = shell.newJob().add(safeCommand).exec()
        return RawProbe(
            id = id,
            exitCode = result.code,
            output = result.out.take(RootSpikeProtocol.MAX_LINES),
        )
    }

    /**
     * Runs an allowlisted command under a hard deadline. On expiry the job is cancelled
     * and the shell is closed so no work continues in the background.
     */
    private fun runTimedProbe(
        shell: Shell,
        id: String,
        command: String,
        timeoutSeconds: Long,
    ): RawProbe {
        val safeCommand = RootSpikeProtocol.requireAllowlisted(command)
        val future = timedProbeExecutor.submit(
            Callable {
                shell.newJob().add(safeCommand).exec()
            },
        )
        return try {
            val result = future.get(timeoutSeconds, TimeUnit.SECONDS)
            RawProbe(
                id = id,
                exitCode = result.code,
                output = result.out.take(RootSpikeProtocol.MAX_LINES),
            )
        } catch (_: TimeoutException) {
            future.cancel(true)
            shell.close()
            activeShell = null
            RawProbe(id = id, exitCode = -1, timedOut = true, output = emptyList())
        } catch (_: Exception) {
            RawProbe(id = id, exitCode = -1, output = emptyList())
        }
    }

    private fun categoryOf(probe: RawProbe, emptyResult: Boolean = false): String = when {
        probe.timedOut -> "timeout"
        probe.exitCode != 0 -> "command_failure"
        emptyResult -> "empty_or_malformed"
        else -> "success"
    }

    private fun deviceInfo(
        selinux: String,
        grantState: String,
        shellStatus: String,
        shellStatusInt: Int,
        shellCachedBefore: Boolean,
    ) = SpikeDeviceInfo(
        androidRelease = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        selinux = selinux,
        libsuGrantState = grantState,
        shellStatus = shellStatus,
        shellStatusInt = shellStatusInt,
        authorizationRequestedByExplicitAction = true,
        shellCachedBefore = shellCachedBefore,
        suVisibleToApp = suVisibleToApp(),
    )

    /**
     * Diagnostic only. A false value is expected on KernelSU Next with default unmount and
     * must never be treated as "device is not rooted" or as an availability decision.
     */
    private fun suVisibleToApp(): Boolean {
        val path = System.getenv("PATH") ?: return false
        return path.split(':').any { entry ->
            entry.isNotBlank() && java.io.File(entry, "su").canExecute()
        }
    }

    private fun failureEvidence(category: String): String = RootSpikeEvidence.format(
        attempt = attempt,
        device = deviceInfo(
            selinux = "unknown",
            grantState = "unknown",
            shellStatus = category,
            shellStatusInt = Shell.UNKNOWN,
            shellCachedBefore = Shell.getCachedShell() != null,
        ),
        probes = listOf(
            SpikeProbeResult(
                id = "harness",
                exitCode = -1,
                timedOut = category == "timeout",
                lineCount = 0,
                recordCount = 0,
                category = category,
            ),
        ),
        recordSource = "none",
        records = emptyList(),
        providerRecordCount = 0,
        isubRecordCount = 0,
    )

    private fun writeEvidence(evidence: String): String {
        val fileName = "traffic-light-root-spike-attempt-$attempt.txt"
        val bytes = evidence.toByteArray(StandardCharsets.UTF_8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create evidence file")
            resolver.openOutputStream(uri, "w")!!.use { output -> output.write(bytes) }
            return "Download/$fileName"
        }
        // MediaStore.Downloads exists only on API 29+. On older releases the spike falls back
        // to app-specific external storage, which needs no runtime permission.
        val directory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: error("Unable to resolve evidence directory")
        val file = java.io.File(directory, fileName)
        file.writeBytes(bytes)
        return file.absolutePath
    }

    private fun showResult(message: String) = runOnUiThread {
        status.text = message
        runButton.isEnabled = true
        runButton.text = "Retry root spike"
    }

    override fun onDestroy() {
        activeShell?.close()
        commandExecutor.shutdownNow()
        timedProbeExecutor.shutdownNow()
        waitExecutor.shutdownNow()
        super.onDestroy()
    }

    private data class RawProbe(
        val id: String,
        val exitCode: Int,
        val timedOut: Boolean = false,
        val output: List<String>,
    ) {
        fun summary(recordCount: Int, category: String) = SpikeProbeResult(
            id = id,
            exitCode = exitCode,
            timedOut = timedOut,
            lineCount = output.size,
            recordCount = recordCount,
            category = category,
        )
    }

    private companion object {
        const val SPIKE_TIMEOUT_SECONDS = 45L
        const val SHELL_START_TIMEOUT_SECONDS = 30L
        const val INJECTED_TIMEOUT_SECONDS = 1L
    }
}
