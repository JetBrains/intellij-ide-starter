package com.intellij.ide.starter.process.exec

import com.intellij.ide.starter.coroutine.perTestSupervisorScope
import com.intellij.ide.starter.utils.catchAll
import com.intellij.ide.starter.utils.getThrowableText
import com.intellij.tools.ide.common.logError
import com.intellij.tools.ide.common.logOutput
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import java.io.IOException
import java.lang.Runnable
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ProcessExecutor(val presentableName: String,
                      val workDir: Path?,
                      val timeout: Duration = 10.minutes,
                      val environmentVariables: Map<String, String> = System.getenv(),
                      val args: List<String>,
                      val errorDiagnosticFiles: List<Path> = emptyList(),
                      val stdoutRedirect: ExecOutputRedirect = ExecOutputRedirect.NoRedirect,
                      val stderrRedirect: ExecOutputRedirect = ExecOutputRedirect.NoRedirect,
                      val onProcessCreated: suspend (Process, Long) -> Unit = { _, _ -> },
                      val onBeforeKilled: suspend (Process, Long) -> Unit = { _, _ -> },
                      val stdInBytes: ByteArray = byteArrayOf(),
                      val onlyEnrichExistedEnvVariables: Boolean = false) {

  private fun redirectProcessOutput(
    process: Process,
    outOrErrStream: Boolean,
    redirectOutput: ExecOutputRedirect
  ): Thread {
    val inputStream = if (outOrErrStream) process.inputStream else process.errorStream
    return thread(start = true, isDaemon = true, name = "Redirect " + (if (outOrErrStream) "stdout" else "stderr")) {
      val reader = inputStream.bufferedReader()
      redirectOutput.open()
      try {
        while (true) {
          val line = try {
            reader.readLine() ?: break
          }
          catch (e: IOException) {
            break
          }
          redirectOutput.redirectLine(line)
        }
      }
      finally {
        redirectOutput.close()
      }
    }
  }

  private fun redirectProcessInput(process: Process, inputBytes: ByteArray): Thread? {
    if (inputBytes.isEmpty()) {
      catchAll { process.outputStream.close() }
      return null
    }

    return thread(start = true, isDaemon = true, name = "Redirect input") {
      catchAll {
        process.outputStream.use {
          it.write(inputBytes)
        }
      }
    }
  }

  private fun ProcessBuilder.actualizeEnvVariables(environmentVariables: Map<String, String> = System.getenv(),
                                                   onlyEnrichExistedEnvVariables: Boolean = false): ProcessBuilder {
    val processEnvironment = environment()
    if (processEnvironment == environmentVariables) return this

    // env variables enrichment
    processEnvironment.putAll(environmentVariables)
    if (!onlyEnrichExistedEnvVariables) {
      val missingKeys = processEnvironment.keys - environmentVariables.keys
      missingKeys.forEach { key -> processEnvironment.remove(key) }
    }

    return this
  }

  private fun killProcessGracefully(process: ProcessHandle) {
    process.destroy()
    runBlocking(Dispatchers.IO) { withTimeout(20.seconds) { process.onExit().await() } }

    process.destroyForcibly()
  }

  private fun analyzeProcessExit(process: Process) {
    val code = process.exitValue()
    if (code != 0) {
      val linesLimit = 100

      logOutput("  ... failed external process `$presentableName` with exit code $code")
      val message = buildString {
        appendLine("External process `$presentableName` failed with code $code")
        for (diagnosticFile in errorDiagnosticFiles.filter { it.exists() && Files.size(it) > 0 }) {
          appendLine(diagnosticFile.fileName.toString())
          appendLine(diagnosticFile.readText().lines().joinToString(System.lineSeparator()) { "  $it" })
        }

        stderrRedirect.read().lines().apply {
          take(linesLimit).dropWhile { it.trim().isBlank() }.let { lines ->
            if (lines.isNotEmpty()) {
              appendLine("  FIRST $linesLimit lines of the standard error stream")
              lines.forEach { appendLine("    $it") }
            }
          }

          if (size > linesLimit) {
            appendLine("...")

            takeLast(linesLimit).dropWhile { it.trim().isBlank() }.let { lines ->
              if (lines.isNotEmpty()) {
                appendLine("  LAST $linesLimit lines of the standard error stream")
                lines.forEach { appendLine("    $it") }
              }
            }
          }
        }

        stdoutRedirect.read().lines().takeLast(linesLimit).dropWhile { it.trim().isEmpty() }.let { lines ->
          if (lines.isNotEmpty()) {
            appendLine("  LAST $linesLimit lines of the standard output stream")
            lines.forEach { appendLine("    $it") }
          }
        }
      }
      error(message)
    }

    logOutput("  ... successfully finished external process for `$presentableName` with exit code 0")
  }

  /**
   * Creates new process and wait for it's completion
   */
  @Throws(ExecTimeoutException::class)
  fun start() {
    require(args.isNotEmpty()) { "Arguments must be not empty to start external process `$presentableName`" }

    val processBuilder = ProcessBuilder()
      .directory(workDir?.toFile())
      .command(*args.toTypedArray())
      .redirectInput(ProcessBuilder.Redirect.PIPE)
      .redirectOutput(ProcessBuilder.Redirect.PIPE)
      .redirectError(ProcessBuilder.Redirect.PIPE)
      .actualizeEnvVariables(environmentVariables, onlyEnrichExistedEnvVariables)

    logOutput(buildString {
      appendLine("Starting external process for `$presentableName`")
      appendLine("  Working directory: $workDir")
      appendLine("  Arguments: [${args.joinToString(separator = " ")}]")
      appendLine(
        "  STDOUT will be redirected to: $stdoutRedirect. STDERR will be redirected to: $stderrRedirect. STDIN is empty: ${stdInBytes.isEmpty()}"
      )
      appendLine(
        "  Environment variables: [${processBuilder.environment().entries.joinToString { "${it.key}=${it.value}" }}]"
      )
    })

    val process = processBuilder.start()

    val processId = process.pid()
    val onProcessCreatedJob: Job = perTestSupervisorScope.launch {
      logOutput("  ... started external process `$presentableName` with process ID = $processId")
      onProcessCreated(process, processId)
    }

    val inputThread = redirectProcessInput(process, stdInBytes)
    val stdoutThread = redirectProcessOutput(process, true, stdoutRedirect)
    val stderrThread = redirectProcessOutput(process, false, stderrRedirect)
    val ioThreads = listOfNotNull(inputThread, stdoutThread, stderrThread)

    fun killProcess() {
      catchAll { runBlocking(Dispatchers.IO) { onProcessCreatedJob.cancelAndJoin() } }
      catchAll { runBlocking(Dispatchers.IO) { withTimeout(1.minutes) { onBeforeKilled(process, processId) } } }
      process.descendants().forEach { catchAll { killProcessGracefully(it) } }
      catchAll { killProcessGracefully(process.toHandle()) }
      catchAll { ioThreads.forEach { it.interrupt() } }
    }

    val stopper = Runnable {
      logOutput(
        "   ... terminating process `$presentableName` by request from external process (either SIGTERM or SIGKILL is caught) ...")
      killProcess()
    }

    val stopperThread = Thread(stopper, "process-shutdown-hook")
    try {
      Runtime.getRuntime().addShutdownHook(stopperThread)
    }
    catch (e: IllegalStateException) {
      logError("Process: $presentableName. Shutdown hook cannot be added because: ${e.message}")
    }

    try {
      if (!runCatching { process.waitFor(timeout.inWholeSeconds, TimeUnit.SECONDS) }.getOrDefault(false)) {
        stopperThread.apply {
          start()
          join(20.seconds.inWholeMilliseconds)
        }
        throw ExecTimeoutException(args.joinToString(" "), timeout)
      }
    }
    finally {
      process.destroyForcibly()

      try {
        Runtime.getRuntime().removeShutdownHook(stopperThread)
      }
      catch (ise: java.lang.IllegalStateException) {
        // generate less noisy stacktraces on IDE shutdown
        val message = ise.message ?: ""
        if (message.startsWith("External process `jstack` failed with code ")
            || message.startsWith("Shutdown in progress")) {
          logOutput("... " + ise.message)
        }
        else {
          logError("CatchAll swallowed error: ${ise.message}")
          logError(getThrowableText(ise))
        }
      }
      catch (t: Throwable) {
        logError("CatchAll swallowed error: ${t.message}")
        logError(getThrowableText(t))
      }
    }

    ioThreads.forEach { catchAll { it.join() } }

    analyzeProcessExit(process)
  }
}