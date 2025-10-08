package com.intellij.ide.starter.process

import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.utils.catchAll
import com.intellij.openapi.diagnostic.Logger
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.system.OS
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal object ProcessKiller {
  private val logger = Logger.getInstance(ProcessKiller::class.java)

  fun killPids(
    pids: Set<Long>,
    workDir: Path? = null,
    timeout: Duration = 1.minutes,
  ): Boolean {
    check(pids.isNotEmpty())
    val results = pids.map { pid ->
      val processInfo = ProcessInfo.create(pid)
      if (processInfo.processHandle != null) {
        killProcessUsingHandle(processInfo.processHandle, timeout)
      }
      else {
        killProcessUsingCommandLine(pid, workDir, timeout)
      }
    }

    return results.all { it }
  }

  fun killProcessUsingCommandLine(
    pid: Long,
    workDir: Path? = null,
    timeout: Duration,
  ): Boolean {
    logOutput("Killing process $pid using command line")

    val args: List<String> = if (OS.CURRENT == OS.Windows) {
      listOf("taskkill", "/pid", pid.toString(), "/f")
    }
    else {
      listOf("kill", "-9", pid.toString())
    }

    val stdout = ExecOutputRedirect.ToStdOutAndString("[kill-pid-${pid}]")
    val stderr = ExecOutputRedirect.ToStdOutAndString("[kill-pid-${pid}]")

    ProcessExecutor(
      presentableName = "Kill Process $pid",
      workDir = workDir,
      timeout = timeout,
      args = args,
      stdoutRedirect = stdout,
      stderrRedirect = stderr,
    ).start()

    val errorMsg = stderr.read()
    return if (errorMsg.isNotEmpty()) {
      logger.warn("Process kill command reported errors: $errorMsg")
      false
    }
    else {
      true
    }
  }

  fun killProcessUsingHandle(processHandle: ProcessHandle, timeout: Duration = 30.seconds): Boolean {
    logOutput("Kill process by pid '${processHandle.pid()}' using ProcessHandle")
    processHandle.destroy()
    catchAll {
      logOutput("Start waiting on exit for process '${processHandle.pid()}'")
      // Usually daemons wait 2 requests for 10 seconds after ide shutdown
      processHandle.onExit().get(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
      logOutput("Finish waiting on exit for process '${processHandle.pid()}'")
    }
    processHandle.destroyForcibly()
    return true
  }
}
