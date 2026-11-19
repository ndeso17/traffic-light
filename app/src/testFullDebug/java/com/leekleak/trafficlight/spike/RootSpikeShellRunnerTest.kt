package com.leekleak.trafficlight.spike

import com.topjohnwu.superuser.Shell
import java.io.InputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Future
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RootSpikeShellRunnerTest {
    @Test
    fun executeRegistersOutputSinksBeforeRunningJob() {
        val job = RecordingJob(stdout = listOf("0"), stderr = listOf("diagnostic"), exitCode = 0)

        val result = RootSpikeShellRunner.execute(job)

        assertTrue(job.outputSinksRegistered)
        assertEquals(0, result.exitCode)
        assertEquals(listOf("0"), result.stdout)
        assertEquals(listOf("diagnostic"), result.stderr)
    }

    private class RecordingJob(
        private val stdout: List<String>,
        private val stderr: List<String>,
        private val exitCode: Int,
    ) : Shell.Job() {
        private var outSink: MutableList<String>? = null
        private var errSink: MutableList<String>? = null
        var outputSinksRegistered = false
            private set

        override fun to(output: MutableList<String>?): Shell.Job = apply {
            outSink = output
            outputSinksRegistered = true
        }

        override fun to(output: MutableList<String>?, error: MutableList<String>?): Shell.Job = apply {
            outSink = output
            errSink = error
            outputSinksRegistered = true
        }

        override fun add(vararg commands: String): Shell.Job = this
        override fun add(input: InputStream): Shell.Job = this

        override fun exec(): Shell.Result {
            outSink?.addAll(stdout)
            errSink?.addAll(stderr)
            return object : Shell.Result() {
                override fun getOut(): MutableList<String> = outSink ?: mutableListOf()
                override fun getErr(): MutableList<String> = errSink ?: mutableListOf()
                override fun getCode(): Int = exitCode
            }
        }

        override fun submit(executor: Executor?, callback: Shell.ResultCallback?) {
            executor?.execute { callback?.onResult(exec()) }
        }

        override fun enqueue(): Future<Shell.Result> = CompletableFuture.completedFuture(exec())
    }
}
