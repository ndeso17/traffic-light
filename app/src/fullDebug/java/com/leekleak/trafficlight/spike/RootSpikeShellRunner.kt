package com.leekleak.trafficlight.spike

import com.topjohnwu.superuser.Shell

/**
 * Executes one allowlisted command on a root shell.
 *
 * libsu 6 only fills the output lists of a job when the caller registers them through
 * [Shell.Job.to]. A job that was never given a sink still reports an exit code, but every
 * output line is dropped, so the sinks are registered here before the job is executed.
 */
internal object RootSpikeShellRunner {
    data class Output(
        val exitCode: Int,
        val stdout: List<String>,
        val stderr: List<String>,
    )

    fun execute(job: Shell.Job): Output {
        val stdout = mutableListOf<String>()
        val stderr = mutableListOf<String>()
        job.to(stdout, stderr)
        val result = job.exec()
        return Output(
            exitCode = result.code,
            stdout = stdout.toList(),
            stderr = stderr.toList(),
        )
    }
}
