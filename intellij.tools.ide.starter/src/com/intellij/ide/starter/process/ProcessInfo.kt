package com.intellij.ide.starter.process

import com.intellij.util.containers.orNull
import oshi.software.os.OSProcess
import java.nio.file.Path
import java.time.Instant
import kotlin.jvm.optionals.getOrNull

class ProcessInfo private constructor(
  val pid: Long,
  val commandLine: String,
  val command: String,
  val arguments: List<String>?,
  private val startTime: Instant?,
  private val user: String?,
  val processHandle: ProcessHandle? = null,
  private val portThatIsUsedByProcess: Int? = null,
) {

  companion object {
    fun create(pid: Long, portThatIsUsedByProcess: Int? = null): ProcessInfo {
      val processHandle = ProcessHandle.of(pid).getOrNull() // null if the process doesn't exist
      val processHandleInfo = processHandle?.info()
      if (processHandleInfo == null) {
        return ProcessInfo(pid, "Not Available", "Not Available", null, null, null, null, portThatIsUsedByProcess)
      }
      else {
        return ProcessInfo(pid = pid,
                           commandLine = processHandleInfo.commandLine().orNull().toString(),
                           command = processHandleInfo.command().orNull().toString(),
                           arguments = processHandleInfo.arguments().orNull()?.toList(),
                           startTime = processHandleInfo.startInstant().orNull(),
                           user = processHandleInfo.user().orNull(),
                           processHandle = processHandle,
                           portThatIsUsedByProcess = portThatIsUsedByProcess)
      }
    }

    fun create(opProcess: OSProcess): ProcessInfo {
      return ProcessInfo(pid = opProcess.processID.toLong(),
                         commandLine = opProcess.commandLine,
                         command = opProcess.name,
                         arguments = opProcess.arguments,
                         startTime = Instant.ofEpochMilli(opProcess.startTime),
                         user = opProcess.user,
                         processHandle = ProcessHandle.of(opProcess.processID.toLong()).getOrNull())
    }
  }

  val shortCommand: String = Path.of(command).fileName?.toString() ?: command

  override fun toString(): String = "$pid $shortCommand"

  val description: String = buildString {
    appendLine("PID: $pid")
    if (portThatIsUsedByProcess != null) {
      appendLine("Port that is used by a process: $portThatIsUsedByProcess")
    }
    appendLine("Command: ${command}")
    if (arguments != null) {
      appendLine("Arguments: $arguments")
    }
    if (!commandLine.contains(command) || arguments?.map { commandLine.contains(it) }?.all { it } != true) {
      appendLine("Command line: $commandLine")
    }
    appendLine("Start time: ${startTime ?: "N/A"}")
    appendLine("User: ${user}")
  }
}