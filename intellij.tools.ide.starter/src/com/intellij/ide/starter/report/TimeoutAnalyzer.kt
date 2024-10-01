package com.intellij.ide.starter.report

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.teamcity.TeamCityCIServer
import com.intellij.ide.starter.ci.teamcity.TeamCityClient
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.utils.replaceSpecialCharactersWithHyphens
import com.intellij.ide.starter.utils.threadDumpParser.ThreadDumpParser
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

object TimeoutAnalyzer {

  private val dialogMethodCalls: List<String> = listOf(
    "com.intellij.openapi.ui.DialogWrapper.doShow",
    "java.awt.Dialog.show"
  )

  fun analyzeTimeout(runContext: IDERunContext ): Error? {
    return detectIdeNotStarted(runContext) ?: detectDialog(runContext)
  }

  private fun detectDialog(runContext: IDERunContext): Error? {
    val threadDump = getLastThreadDump(runContext) ?: return null
    val threadDumpParsed = ThreadDumpParser.parse(threadDump)
    val edtThread = threadDumpParsed.first() { it.isEDT() }

    if (dialogMethodCalls.any { call -> edtThread.getStackTrace().contains(call) }) {
      val lastCommandNote = getLastCommand(runContext)?.let { System.lineSeparator() + "Last executed command was: $it" } ?: ""
      val errorMessage = "Timeout of IDE run '${runContext.contextName}' for ${runContext.runTimeout} due to a dialog being shown.$lastCommandNote"
      val error = Error(errorMessage, edtThread.getStackTrace(), threadDump, ErrorType.TIMEOUT)
      postLastScreenshots(runContext)
      return error
    }
    else return null
  }

  private fun detectIdeNotStarted(runContext: IDERunContext) : Error? {
    if (!runContext.logsDir.resolve("idea.log").exists()) {
      return Error(
        "Timeout of IDE run '${runContext.contextName}' for ${runContext.runTimeout}. No idea.log file present in log directory",
        "",
        "",
        ErrorType.TIMEOUT
      )
    } else return null
  }

  private fun postLastScreenshots(runContext: IDERunContext) {
    if (!CIServer.instance.isBuildRunningOnCI) return
    val screenshotFolder = runContext.logsDir.resolve("screenshots").takeIf { it.exists() } ?: return
    val heartbeats = screenshotFolder.listDirectoryEntries("heartbeat*").sortedBy { it.name }.last { it.listDirectoryEntries().isNotEmpty() }

    val screenshots = heartbeats.listDirectoryEntries()
    screenshots.forEach { screenshot ->

      TeamCityClient.publishTeamCityArtifacts(
        screenshot,
        runContext.contextName.replaceSpecialCharactersWithHyphens() + "/timeout-screenshots",
        screenshot.name,
        false
      )

      TeamCityCIServer.addTestMetadata(
        testName = null,
        TeamCityCIServer.TeamCityMetadataType.IMAGE,
        flowId = null,
        name = null,
        value = runContext.contextName.replaceSpecialCharactersWithHyphens() + "/timeout-screenshots/${screenshot.name}"
      )
    }
  }

  private fun getLastThreadDump(runContext: IDERunContext): String? {
    val killThreadDump = runContext.logsDir.listDirectoryEntries("threadDump-before-kill*.txt").firstOrNull()

    val threadDumpsDirectory = runContext.logsDir.resolve("monitoring-thread-dumps-ide")
    val lastThreadDump = threadDumpsDirectory
      .takeIf { it.exists() }
      ?.listDirectoryEntries("threadDump*.txt")
      ?.maxByOrNull { it.name }

    return (killThreadDump ?: lastThreadDump)?.let(Files::readString)
  }

  private fun getLastCommand(runContext: IDERunContext): String? {
    val lastLog = runContext.logsDir.resolve("idea.log")
    if (!lastLog.exists()) return null
    val allLogs = listOf(lastLog) + runContext.logsDir.listDirectoryEntries("idea.*.log").sortedBy { it.name }
    return allLogs.firstNotNullOfOrNull { logFile ->
      Files.readString(logFile)
        .lineSequence()
        .filter { "CommandLogger - %" in it }
        .lastOrNull()
        ?.substringAfterLast("CommandLogger - %")
    }
  }
}
