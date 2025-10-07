package com.intellij.ide.starter.utils

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.system.OS
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Path

object PortUtil {
  private val logger = Logger.getInstance(PortUtil::class.java)

  fun isPortAvailable(host: InetAddress, port: Int): Boolean {
    return try {
      ServerSocket(port, 0, host).use { /* bound successfully */ }
      true
    }
    catch (_: Exception) {
      false
    }
  }

  /**
   * Finds an available port starting from the proposed port on a specified host.
   * The separate error will be reported if the proposed port is not available.
   *
   * @throws IllegalStateException If no available ports are found within the specified range.
   */
  fun getAvailablePort(host: InetAddress = InetAddress.getLoopbackAddress(), proposedPort: Int): Int {
    if (isPortAvailable(host, proposedPort)) {
      return proposedPort
    }
    else {
      val pids = getProcessesUsingPort(proposedPort)

      val pidsInfo = pids?.mapNotNull { pid ->
        ProcessHandle.of(pid.toLong()).map { it.info() }.orElse(null)?.let {
          pid to ProcessInfo(
            pid = pid.toLong(),
            port = proposedPort,
            pi = it,
          )
        }
      }?.toMap()
      val pidsWithProcessName = pids?.map { pid -> "$pid (${pidsInfo?.get(pid)?.shortProcessName ?: "N/A"})" }
      val processNames = pids?.map { pid -> pidsInfo?.get(pid)?.shortProcessName ?: "N/A" }?.distinct()?.sorted()?.joinToString() ?: "Failed to retrieve processes"
      CIServer.instance.reportTestFailure(
        testName = "Proposed port is not available on host as it used by processes: ${processNames}",
        message = buildString {
          appendLine("Proposed port $proposedPort is not available on host $host as it used by processes: ${pidsWithProcessName?.joinToString(", ") ?: "Failed to retrieve processes"}")
          appendLine("Busy port could mean that the previous process is still running or the port is blocked by another application.")
          appendLine("Please make sure to investigate, the uninvestigated hanging processes could lead to further unclear test failure.")
          appendLine("PLEASE BE CAREFUL WHEN MUTING")
        },
        details = buildString {
          if (pidsInfo != null) {
            appendLine("Processes using the port $proposedPort:")
            pidsInfo.forEach { (_, info) -> appendLine(info.description) }
          }
          appendLine(Throwable().stackTraceToString())
        }
      )

      repeat(100) {
        if (isPortAvailable(host, proposedPort + it)) {
          return proposedPort + it
        }
      }
      error("No available port found in a range $proposedPort..${proposedPort + 100}")
    }
  }

  /**
   * Retrieves a list of process IDs that are using a specific network port on the system.
   *
   * @param port The network port to check for processes.
   * @return A list of process IDs that are using the specified port, or null if an error occurs.
   */
  fun getProcessesUsingPort(port: Int): List<Int>? {
    var errorMsg = ""

    return runCatching {
      val findCommand = if (OS.CURRENT == OS.Windows) {
        listOf("cmd", "/c", "netstat -ano | findstr :$port")
      }
      else {
        listOf("sh", "-c", "lsof -i :$port -t")
      }

      val prefix = "find-pid"
      val stdoutRedirectFind = ExecOutputRedirect.ToStdOutAndString(prefix)
      val stderrRedirectFind = ExecOutputRedirect.ToStdOutAndString(prefix)

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
        processIds.split("\n").mapNotNull { it.removePrefix(prefix).trim().split("\\s+".toRegex())[4].toIntOrNull() }
      }
      else {
        processIds.split("\n").mapNotNull { it.removePrefix(prefix).trim().toIntOrNull() }
      }
    }.getOrElse {
      logger.error("An error occurred while attempting to get processes using port: $port. Error message: ${it.message}. Error message: $errorMsg: ${it.stackTraceToString()}")
      return null
    }
  }

  fun killProcessesUsingPort(port: Int): Boolean {
    val processIds = getProcessesUsingPort(port)

    if (processIds?.isNotEmpty() == true) {
      return catchAll {
        val killCommand = if (OS.CURRENT == OS.Windows) {
          listOf("cmd", "/c", "taskkill") + processIds.flatMap { listOf("/PID", it.toString()) }
        }
        else {
          listOf("kill", "-9") + processIds.map { it.toString() }
        }

        val stdoutRedirectKill = ExecOutputRedirect.ToStdOutAndString("kill-pid")
        val stderrRedirectKill = ExecOutputRedirect.ToStdOutAndString("kill-pid")

        ProcessExecutor(
          "Kill Processes Using Port",
          workDir = null,
          stdoutRedirect = stdoutRedirectKill,
          stderrRedirect = stderrRedirectKill,
          args = killCommand
        ).start()

        val errorMsg = stderrRedirectKill.read()
        if (stderrRedirectKill.read().isEmpty()) {
          logger.info("Successfully killed processes using port: $port")
          true
        }
        else {
          logger.error("Failed to kill processes using port: $port. Error message: $errorMsg")
          false
        }
      } == true
    }
    else {
      if (processIds == null) {
        logger.error("Failed to retrieve processes using port: $port")
      }
      else {
        logger.error("No processes found using port: $port")
      }
      return false
    }
  }
}

data class ProcessInfo(
  val port: Int,
  val pid: Long,
  val pi: ProcessHandle.Info,
) {
  val shortProcessName: String = Path.of(pi.command().orElse("")).fileName?.toString() ?: "N/A"

  val description: String = buildString {
    appendLine("PID: $pid")
    appendLine("Port: $port")
    appendLine("Command: ${pi.command().orElse("N/A")}")
    appendLine("Arguments: ${pi.arguments().orElse(emptyArray()).joinToString(" ")}")
    appendLine("Start time: ${pi.startInstant().orElse(null)}")
    appendLine("User: ${pi.user().orElse("N/A")}")
  }
}