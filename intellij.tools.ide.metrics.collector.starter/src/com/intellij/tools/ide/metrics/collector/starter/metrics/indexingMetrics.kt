package com.intellij.tools.ide.metrics.collector.starter.metrics

import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.starter.collector.StarterTelemetryJsonMeterCollector
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper
import com.intellij.util.indexing.diagnostic.dto.IndexingMetrics
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.name

fun IndexingMetrics.getListOfIndexingMetrics(ideStartResult: IDEStartResult): List<PerformanceMetrics.Metric> {
  val numberOfIndexedFiles = totalNumberOfIndexedFiles
  val numberOfFilesFullyIndexedByExtensions = totalNumberOfFilesFullyIndexedByExtensions
  return listOf(
    PerformanceMetrics.newDuration("indexingTimeWithoutPauses", durationMillis = totalIndexingTimeWithoutPauses.toInt()),
    PerformanceMetrics.newDuration("scanningTimeWithoutPauses", durationMillis = totalScanFilesTimeWithoutPauses.toInt()),
    PerformanceMetrics.newDuration("pausedTimeInIndexingOrScanning", durationMillis = totalPausedTime.toInt()),
    PerformanceMetrics.newDuration("dumbModeTimeWithPauses", durationMillis = totalDumbModeTimeWithPauses.toInt()),
    PerformanceMetrics.newCounter("numberOfIndexedFiles", value = numberOfIndexedFiles),
    PerformanceMetrics.newCounter("numberOfIndexedFilesWritingIndexValue", value = totalNumberOfIndexedFilesWritingIndexValues),
    PerformanceMetrics.newCounter("numberOfIndexedFilesWithNothingToWrite", value = totalNumberOfIndexedFilesWithNothingToWrite),
    PerformanceMetrics.newCounter("numberOfFilesIndexedByExtensions", value = numberOfFilesFullyIndexedByExtensions),
    PerformanceMetrics.newCounter("numberOfFilesIndexedWithoutExtensions",
                                  value = (numberOfIndexedFiles - numberOfFilesFullyIndexedByExtensions)),
    PerformanceMetrics.newCounter("numberOfRunsOfScannning", value = totalNumberOfRunsOfScanning),
    PerformanceMetrics.newCounter("numberOfRunsOfIndexing", value = totalNumberOfRunsOfIndexing)
  ) + getProcessingSpeedOfFileTypes(processingSpeedPerFileTypeAvg, "Avg") +
         getProcessingSpeedOfFileTypes(processingSpeedPerFileTypeWorst, "Worst") +
         getProcessingSpeedOfBaseLanguages(processingSpeedPerBaseLanguageAvg, "Avg") +
         getProcessingSpeedOfBaseLanguages(processingSpeedPerBaseLanguageWorst, "Worst") +
         getProcessingTimeOfFileType(processingTimePerFileType) +
         collectPerformanceMetricsFromCSV(ideStartResult, "lexer", "lexing") +
         collectPerformanceMetricsFromCSV(ideStartResult, "parser", "parsing")
}

private fun collectPerformanceMetricsFromCSV(
  runResult: IDEStartResult,
  metricPrefixInCSV: String,
  resultingMetricPrefix: String,
): List<PerformanceMetrics.Metric> {
  val timeRegex = Regex("${metricPrefixInCSV}\\.(.+)\\.time\\.ns")
  val time = StarterTelemetryJsonMeterCollector(MetricsSelectionStrategy.SUM) {
    it.name.startsWith("$metricPrefixInCSV.") && it.name.endsWith(".time.ns")
  }.collect(runResult.runContext) { name, value -> name to TimeUnit.NANOSECONDS.toMillis(value).toInt() }.associate {
    val language = timeRegex.find(it.id.name)?.groups?.get(1)?.value
    Pair(language, it.value)
  }
  val sizeRegex = Regex("${metricPrefixInCSV}\\.(.+)\\.size\\.bytes")
  val size = StarterTelemetryJsonMeterCollector(MetricsSelectionStrategy.SUM) {
    it.name.startsWith("$metricPrefixInCSV.") && it.name.endsWith(".size.bytes")
  }.collect(runResult.runContext).associate {
    val language = sizeRegex.find(it.id.name)?.groups?.get(1)?.value
    Pair(language, it.value)
  }
  val speed = time.filter { it.value != 0 }.mapValues {
    size.getValue(it.key) / it.value
  }

  return time.map { PerformanceMetrics.newDuration("${resultingMetricPrefix}Time#" + it.key, it.value) } +
         size.map { PerformanceMetrics.newCounter("${resultingMetricPrefix}Size#" + it.key, it.value) } +
         speed.map { PerformanceMetrics.newCounter("${resultingMetricPrefix}Speed#" + it.key, it.value) }
}

fun extractIndexingMetrics(startResult: IDEStartResult, projectName: String? = null): IndexingMetrics {
  val indexDiagnosticDirectory = startResult.runContext.logsDir / "indexing-diagnostic"
  val indexDiagnosticDirectoryChildren = Files.list(indexDiagnosticDirectory).filter { it.toFile().isDirectory }.use { it.toList() }
  val projectIndexDiagnosticDirectory = indexDiagnosticDirectoryChildren.let { perProjectDirs ->
    if (projectName == null) {
      perProjectDirs.singleOrNull() ?: error("Only one project diagnostic dir is expected: ${perProjectDirs.joinToString()}")
    }
    else {
      perProjectDirs.find { it.name.startsWith("$projectName.") }
    }
  }
  val jsonIndexDiagnostics = Files.list(projectIndexDiagnosticDirectory)
    .use { stream -> stream.filter { it.extension == "json" }.toList() }
    .filter { Files.size(it) > 0L }
    .mapNotNull { IndexDiagnosticDumper.readJsonIndexingActivityDiagnostic(it) }
  return IndexingMetrics(jsonIndexDiagnostics)
}

private fun getProcessingSpeedOfFileTypes(mapFileTypeToSpeed: Map<String, Int>, suffix: String): List<PerformanceMetrics.Metric> =
  mapFileTypeToSpeed.map {
    PerformanceMetrics.newCounter("processingSpeed$suffix#${it.key}", value = it.value)
  }

private fun getProcessingSpeedOfBaseLanguages(mapBaseLanguageToSpeed: Map<String, Int>, suffix: String): List<PerformanceMetrics.Metric> =
  mapBaseLanguageToSpeed.map {
    PerformanceMetrics.newCounter("processingSpeedOfBaseLanguage$suffix#${it.key}", value = it.value)
  }

private fun getProcessingTimeOfFileType(mapFileTypeToDuration: Map<String, Long>): List<PerformanceMetrics.Metric> =
  mapFileTypeToDuration.map {
    PerformanceMetrics.newDuration("processingTime#${it.key}", durationMillis = TimeUnit.NANOSECONDS.toMillis(it.value.toLong()).toInt())
  }

