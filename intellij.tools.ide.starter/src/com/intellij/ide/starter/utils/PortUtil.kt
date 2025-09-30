package com.intellij.ide.starter.utils

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.system.OS
import java.net.InetAddress
import java.net.ServerSocket

object PortUtil {

  fun isPortAvailable(host: InetAddress, port: Int): Boolean {
    return try {
      ServerSocket(port, 0, host).use { /* bound successfully */ }
      true
    }
    catch (_: Exception) {
      false
    }
  }

  fun getAvailablePort(host: InetAddress = InetAddress.getLoopbackAddress(), proposedPort: Int): Int {
    if (isPortAvailable(host, proposedPort)) {
      return proposedPort
    }
    else {
      CIServer.instance.reportTestFailure("Proposed port is not available.",
                                          "Proposed port $proposedPort is not available on host $host as it used by processes: ${getProcessesUsingPort(proposedPort).joinToString(", ")}\n" +
                                          "Busy port could mean that the previous process is still running or the port is blocked by another application.\n" +
                                          "Please make sure to investigate, the uninvestigated hanging processes could lead to further unclear test failure.\n" +
                                          "PLEASE BE CAREFUL WHEN MUTING",
                                          Throwable().stackTraceToString())
      repeat(100) {
        if (isPortAvailable(host, proposedPort + it)) {
          return proposedPort + it
        }
      }
      error("No available port found")
    }
  }

  private fun getProcessesUsingPort(port: Int): List<Int> {
    var errorMsg = ""

    return runCatching {
      val findCommand = if (OS.CURRENT == OS.Windows) {
        listOf("cmd", "/c", "netstat -ano | findstr :$port")
      }
      else {
        listOf("sh", "-c", "lsof -i :$port -t")
      }

      val stdoutRedirectFind = ExecOutputRedirect.ToString()
      val stderrRedirectFind = ExecOutputRedirect.ToString()

      ProcessExecutor(
        "Find Processes Using Port",
        workDir = null,
        stdoutRedirect = stdoutRedirectFind,
        stderrRedirect = stderrRedirectFind,
        args = findCommand
      ).start()

      val processIds = stdoutRedirectFind.read().trim()
      errorMsg = stderrRedirectFind.read()

      if (OS.CURRENT == OS.Windows) {
        processIds.split("\n").map { it.trim().split("\\s+".toRegex())[4].toInt() }
      }
      else {
        processIds.split("\n").map { it.trim().toInt() }
      }
    }.getOrElse {
      throw IllegalStateException("An error occurred while attempting to get processes using port: $port. Error message: ${it.message}. Error message: $errorMsg", it)
    }
  }

  fun killProcessesUsingPort(port: Int): Boolean {
    var errorMsg = ""
    runCatching {
      val processIds = getProcessesUsingPort(port)

      if (processIds.isNotEmpty()) {
        val killCommand = if (OS.CURRENT == OS.Windows) {
          listOf("cmd", "/c", "taskkill /PID") + processIds.joinToString(" ")
        }
        else {
          listOf("kill", "-9") + processIds.joinToString(" ")
        }

        val stdoutRedirectKill = ExecOutputRedirect.ToString()
        val stderrRedirectKill = ExecOutputRedirect.ToString()

        ProcessExecutor(
          "Kill Processes Using Port",
          workDir = null,
          stdoutRedirect = stdoutRedirectKill,
          stderrRedirect = stderrRedirectKill,
          args = killCommand
        ).start()

        errorMsg = stderrRedirectKill.read()
        if (stderrRedirectKill.read().isEmpty()) {
          logOutput("Successfully killed processes using port: $port")
          return true
        }
        else {
          logOutput("Failed to kill processes using port: $port. Error message: $errorMsg")
          return false
        }
      }
      else {
        error("No processes found using port: $port")
      }
    }.getOrElse {
      throw IllegalStateException("An error occurred while attempting to kill processes using port: $port. Error message: ${it.message}. Error message: $errorMsg", it)
    }
  }
}