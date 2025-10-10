package com.intellij.ide.starter.utils

import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.system.OS
import java.net.InetAddress
import java.net.ServerSocket
import com.intellij.ide.starter.process.ProcessInfo
import com.intellij.ide.starter.process.ProcessKiller

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
      val processes = getProcessesUsingPort(proposedPort)

      val pidsInfoMap = processes?.associate { it.pid to it }
      val processNames = pidsInfoMap?.map { it.value.toString() }?.sorted()?.joinToString(", ")
                         ?: "Failed to retrieve processes"

      logger.error(IllegalStateException(
        buildString {
          appendLine("Proposed port $proposedPort is not available on host $host as it used by processes: ${processNames}")
          appendLine("Busy port could mean that the previous process is still running or the port is blocked by another application.")
          appendLine("Please make sure to investigate, the uninvestigated hanging processes could lead to further unclear test failure.")
          appendLine("PLEASE BE CAREFUL WHEN MUTING")

          if (pidsInfoMap != null) {
            appendLine()
            appendLine("Processes using the port $proposedPort:")
            pidsInfoMap.forEach { (_, info) -> appendLine(info.description) }
          }
        })
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
  fun getProcessesUsingPort(port: Int): List<ProcessInfo>? {
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
        args = findCommand,
        analyzeProcessExit = false
      ).start()

      val processIdsRaw = stdoutRedirectFind.read().trim()
      errorMsg = stderrRedirectFind.read()

      val pids: List<Int> = if (OS.CURRENT == OS.Windows) {
        processIdsRaw.split("\n").mapNotNull { line ->
          val tokens = line.removePrefix(prefix).trim().split("\\s+".toRegex())
          tokens.getOrNull(4)?.toIntOrNull()
        }
      }
      else {
        processIdsRaw.split("\n").mapNotNull { it.removePrefix(prefix).trim().toIntOrNull() }
      }

      pids.map { pid ->
        ProcessInfo.create(pid.toLong(), portThatIsUsedByProcess = port)
      }
    }.getOrElse {
      logger.error(buildString {
        appendLine("An error occurred while attempting to get processes using port: $port. ")
        if (errorMsg.isNotEmpty()) {
          appendLine("Error message: $errorMsg")
        }
      }, it)
      return null
    }
  }

  fun killProcessesUsingPort(port: Int): Boolean {
    val processes = getProcessesUsingPort(port)

    if (processes?.isNotEmpty() == true) {
      return killProcesses(processes)
    }
    else {
      if (processes == null) {
        logger.error("Failed to retrieve processes using port: $port")
      }
      else {
        logger.error("No processes using port found: $port")
      }
      return false
    }
  }

  fun killProcesses(processes: List<ProcessInfo>): Boolean {
    return catchAll {
      ProcessKiller.killPids(
        pids = processes.map { it.pid }.toSet(),
      ).also { success ->
        if (success) {
          logger.info("Successfully killed processes ${processes.joinToString(", ")}")
        }
        else {
          logger.error("Failed to kill processes ${processes.joinToString(", ")}")
        }
      }
    } == true
  }
}
