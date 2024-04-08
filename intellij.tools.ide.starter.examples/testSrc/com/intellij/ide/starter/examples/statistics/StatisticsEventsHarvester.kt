package com.intellij.ide.starter.extended.statistics

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.internal.statistic.eventLog.SerializationHelper
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.listDirectoryEntries

/**
 * Retrieves application statistics events (FUS or MLSE) from event log data
 */
class StatisticsEventsHarvester(private val eventLogsDir: Path) {

  constructor(testContext: IDETestContext, recorder: String = "FUS") : this(testContext.paths.systemDir.resolve("event-log-data/logs/$recorder"))

  fun getStatisticEventsByGroup(groupId: String): List<LogEvent> = getStatisticEvents { it.group.id == groupId }

  /**
   * Retrieve statistics events from all event log files at `system/event-log-data/logs/{recorder}`
   */
  fun getStatisticEvents(filter: (LogEvent) -> Boolean = { true }): List<LogEvent> {
    val logs = getStatisticsLogFiles()
    val allEvents = logs.flatMap { deserializeEventLogFile(it) }

    return allEvents.filter(filter)
  }

  private fun getStatisticsLogFiles(): List<Path> {
    require(eventLogsDir.exists()) { "Statistics logs folder must exists at $eventLogsDir" }

    var logFiles = eventLogsDir.listDirectoryEntries(glob = "*.log")
    logFiles = logFiles.sortedWith { path1, path2 ->
      path1.getLastModifiedTime().compareTo(path2.getLastModifiedTime())
    }

    logOutput("Found statistics log files: $logFiles")

    return logFiles
  }

  private fun deserializeEventLogFile(eventLogPath: Path): List<LogEvent> {
    val reader = eventLogPath.bufferedReader()

    return reader.useLines { lines ->
      lines.map {
        try {
          SerializationHelper.deserializeLogEvent(it)
        }
        catch (e: Exception) {
          logError("Statistics log deserialization failure on line: ${System.lineSeparator()}$it")
          throw e
        }
      }.toList()
    }
  }
}
