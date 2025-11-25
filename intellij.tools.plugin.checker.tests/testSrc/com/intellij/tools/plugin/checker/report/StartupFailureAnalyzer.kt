package com.intellij.tools.plugin.checker.report

import com.intellij.ide.starter.report.Error
import com.intellij.ide.starter.report.ErrorType
import com.intellij.ide.starter.runner.IDERunContext
import kotlin.io.path.exists
import kotlin.io.path.readLines

object StartupFailureAnalyzer {

  fun analyzeStartupFailure(runContext: IDERunContext): Error? {
    val ideaLogPath = runContext.logsDir.resolve("idea.log")
    if (!ideaLogPath.exists()) return null

    val logLines = ideaLogPath.readLines()

    val startFailedIndex = logLines.indexOfFirst {
      it.contains("INFO - STDERR -") && it.contains("**Start Failed**")
    }

    if (startFailedIndex == -1) return null

    val errorInfo = extractErrorFromStderr(logLines, startFailedIndex)

    return Error(
      messageText = errorInfo.messageText,
      stackTraceContent = errorInfo.stackTrace,
      threadDump = "",
      type = ErrorType.ERROR
    )
  }

  private data class ErrorInfo(val messageText: String, val stackTrace: String)

  private fun extractErrorFromStderr(logLines: List<String>, startFailedIndex: Int): ErrorInfo {
    val stderrLines = mutableListOf<String>()

    for (i in (startFailedIndex + 4) until logLines.size) {
      val line = logLines[i]

      if (!line.contains("INFO - STDERR -")) break

      val stderrContent = line.substringAfter("INFO - STDERR - ", "")
      stderrLines.add(stderrContent)
    }

    return ErrorInfo(stderrLines.first(), stderrLines.joinToString("\n"))
  }
}
