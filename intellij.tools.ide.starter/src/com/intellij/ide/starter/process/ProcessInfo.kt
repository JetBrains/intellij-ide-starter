package com.intellij.ide.starter.process

import oshi.SystemInfo
import oshi.software.os.OSProcess
import java.nio.file.Path
import java.time.Instant
import kotlin.jvm.optionals.getOrNull

class ProcessInfo private constructor(
  val pid: Long,
  val commandLine: String,
  val command: String,
  val arguments: List<String>,
  private val startTime: Instant?,
  private val user: String?,
  val processHandle: ProcessHandle? = null,
  private val portThatIsUsedByProcess: Int? = null,
) {

  companion object {
    fun create(pid: Long, portThatIsUsedByProcess: Int? = null): ProcessInfo {
      val opProcess = SystemInfo().operatingSystem.getProcess(pid.toInt()) // null if the process doesn't exist
      if (opProcess == null) {
        return ProcessInfo(pid, "Not Available", "Not Available", emptyList(), null, null, null, portThatIsUsedByProcess)
      }
      else {
        return create(opProcess, portThatIsUsedByProcess)
      }
    }

    fun create(opProcess: OSProcess, portThatIsUsedByProcess: Int? = null): ProcessInfo {
      return ProcessInfo(pid = opProcess.processID.toLong(),
                         commandLine = opProcess.commandLine,
                         command = opProcess.name,
                         arguments = opProcess.arguments,
                         startTime = Instant.ofEpochMilli(opProcess.startTime),
                         user = opProcess.user,
                         processHandle = ProcessHandle.of(opProcess.processID.toLong()).getOrNull(),
                         portThatIsUsedByProcess = portThatIsUsedByProcess)
    }
  }

  override fun equals(other: Any?): Boolean {
    if (other !is ProcessInfo) return false
    return pid == other.pid
  }

  override fun hashCode(): Int = pid.hashCode()

  val shortCommand: String = Path.of(command).fileName?.toString() ?: command

  override fun toString(): String = "$pid $shortCommand"

  val description: String = buildString {
    appendLine("PID: $pid")
    if (portThatIsUsedByProcess != null) {
      appendLine("Port that is used by a process: $portThatIsUsedByProcess")
    }
    appendLine("Command: $command")
    appendLine("Arguments: $arguments")
    appendLine("Command line: $commandLine")
    appendLine("Start time: $startTime")
    appendLine("User: $user")
  }
}