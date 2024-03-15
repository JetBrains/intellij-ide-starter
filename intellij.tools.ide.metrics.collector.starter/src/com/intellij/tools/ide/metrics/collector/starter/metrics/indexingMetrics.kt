package com.intellij.tools.ide.metrics.collector.starter.metrics

import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.openapi.util.text.StringUtil
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.starter.collector.StarterTelemetryJsonMeterCollector
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper
import com.intellij.util.indexing.diagnostic.dto.*
import com.intellij.util.indexing.diagnostic.dto.JsonFileProviderIndexStatistics.JsonIndexedFile
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePath
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.name

/*
 * metricNumberOfIndexedFilesWritingIndexValue <= metricNumberOfIndexedFiles
 *
 * A file sent to indexing is considered indexed;
 * When a new value of some index of a file is written, or outdated value deleted,
 * the file adds to the metricNumberOfIndexedFilesWritingIndexValue
 */
data class IndexingMetrics(
  val ideStartResult: IDEStartResult,
  val jsonIndexDiagnostics: List<JsonIndexingActivityDiagnostic>
) {
  val scanningHistories: List<JsonProjectScanningHistory>
    get() = jsonIndexDiagnostics.map { it.projectIndexingActivityHistory }.filterIsInstance<JsonProjectScanningHistory>()
  private val indexingHistories: List<JsonProjectDumbIndexingHistory>
    get() = jsonIndexDiagnostics.map { it.projectIndexingActivityHistory }.filterIsInstance<JsonProjectDumbIndexingHistory>()
  private val scanningStatistics: List<JsonScanningStatistics>
    get() = jsonIndexDiagnostics.map { it.projectIndexingActivityHistory }.filterIsInstance<JsonProjectScanningHistory>()
      .flatMap { history -> history.scanningStatistics }

  val totalNumberOfRunsOfScanning: Int
    get() = scanningHistories.count { it.projectName.isNotEmpty() }

  val totalNumberOfRunsOfIndexing: Int
    get() = indexingHistories.count { it.projectName.isNotEmpty() }

  private val totalDumbModeTimeWithPauses: Long
    get() = jsonIndexDiagnostics.sumOf {
      TimeUnit.NANOSECONDS.toMillis(it.projectIndexingActivityHistory.times.dumbWallTimeWithPauses.nano)
    }

  val totalTimeOfScanningOrIndexing: Long
    get() = jsonIndexDiagnostics.sumOf {
      TimeUnit.NANOSECONDS.toMillis(it.projectIndexingActivityHistory.times.totalWallTimeWithPauses.nano)
    }

  val totalIndexingTimeWithoutPauses: Long
    get() = TimeUnit.NANOSECONDS.toMillis(indexingHistories.sumOf { it.times.totalWallTimeWithoutPauses.nano })

  val totalScanFilesTimeWithoutPauses: Long
    get() = TimeUnit.NANOSECONDS.toMillis(scanningHistories.sumOf { it.times.totalWallTimeWithoutPauses.nano })

  private val totalPausedTime: Long
    get() = TimeUnit.NANOSECONDS.toMillis(jsonIndexDiagnostics.sumOf { it.projectIndexingActivityHistory.times.wallTimeOnPause.nano })

  val totalNumberOfIndexedFiles: Int
    get() = indexingHistories.sumOf { history -> history.fileProviderStatistics.sumOf { it.totalNumberOfIndexedFiles } }

  val totalNumberOfIndexedFilesWritingIndexValues: Int
    get() = indexingHistories.sumOf { history -> history.fileProviderStatistics.sumOf { it.totalNumberOfIndexedFiles - it.totalNumberOfNothingToWriteFiles } }

  val indexedFiles: List<JsonIndexedFile>
    get() = indexingHistories.flatMap { history -> history.fileProviderStatistics.flatMap { it.indexedFiles ?: emptyList() } }

  val totalNumberOfScannedFiles: Int
    get() = scanningStatistics.sumOf { it.numberOfScannedFiles }

  val totalNumberOfFilesFullyIndexedByExtensions: Int
    get() = jsonIndexDiagnostics.sumOf {
      when (val fileCount = it.projectIndexingActivityHistory.fileCount) {
        is JsonProjectScanningFileCount -> fileCount.numberOfFilesIndexedByInfrastructureExtensionsDuringScan
        is JsonProjectDumbIndexingFileCount -> fileCount.numberOfFilesIndexedByInfrastructureExtensionsDuringIndexingStage
      }
    }

  val listOfFilesFullyIndexedByExtensions: List<String>
    get() {
      val indexedFiles = mutableListOf<String>()
      jsonIndexDiagnostics.forEach { diagnostic ->
        when (val history = diagnostic.projectIndexingActivityHistory) {
          is JsonProjectScanningHistory -> history.scanningStatistics.forEach {
            indexedFiles.addAll(it.filesFullyIndexedByInfrastructureExtensions)
          }
          is JsonProjectDumbIndexingHistory -> {
            history.fileProviderStatistics.forEach {
              indexedFiles.addAll(it.filesFullyIndexedByExtensions)
            }
          }
        }
      }
      return indexedFiles.distinct()
    }

  val numberOfIndexedByExtensionsFilesForEachProvider: Map<String, Int>
    get() {
      val indexedByExtensionsFiles = mutableMapOf<String /* Provider name */, Int /* Number of files indexed by extensions */>()
      scanningStatistics.forEach { stat ->
        indexedByExtensionsFiles[stat.providerName] = indexedByExtensionsFiles.getOrDefault(stat.providerName, 0) +
                                                      stat.numberOfFilesFullyIndexedByInfrastructureExtensions
      }
      return indexedByExtensionsFiles
    }

  val numberOfIndexedFilesByUsualIndexesPerProvider: Map<String, Int>
    get() {
      val indexedFiles = mutableMapOf<String /* Provider name */, Int /* Number of files indexed by usual indexes */>()
      indexingHistories.flatMap { it.fileProviderStatistics }.forEach { indexStats ->
        indexedFiles[indexStats.providerName] = indexedFiles.getOrDefault(indexStats.providerName,
                                                                          0) + indexStats.totalNumberOfIndexedFiles
      }
      return indexedFiles
    }

  val scanningStatisticsByProviders: Map<String, ScanningStatistics>
    get() {
      val indexedFiles = mutableMapOf<String /* Provider name */, ScanningStatistics>()
      scanningStatistics.forEach { stats ->
        val value: ScanningStatistics = indexedFiles.getOrDefault(stats.providerName, ScanningStatistics())
        indexedFiles[stats.providerName] = value.merge(stats)
      }
      return indexedFiles
    }

  val numberOfFullRescanning: Int
    get() = scanningHistories.count { it.times.scanningType.isFull }

  val allIndexedFiles: Map<String, List<PortableFilePath>> //without shared indexes
    get() {
      val indexedFiles = hashMapOf<String /* Provider name */, MutableList<PortableFilePath>>()
      indexingHistories.flatMap { it.fileProviderStatistics }.forEach { fileProviderStatistic ->
        indexedFiles.getOrPut(fileProviderStatistic.providerName) { arrayListOf() } +=
          fileProviderStatistic.indexedFiles.orEmpty().map { it.path }
      }
      return indexedFiles
    }

  private val processingSpeedPerFileTypeWorst: Map<String, Int>
    get() {
      return indexingHistories.flatMap { it.totalStatsPerFileType }.groupBy { it.fileType }.mapValues {
        it.value.minOf { jsonStatsPerFileType -> jsonStatsPerFileType.totalProcessingSpeed.toKiloBytesPerSecond() }
      }
    }

  private val processingSpeedPerFileTypeAvg: Map<String, Int>
    get() {
      return indexingHistories.flatMap { history ->
        history.totalStatsPerFileType.map {
          Triple(it.fileType, it.partOfTotalProcessingTime.partition * history.times.totalWallTimeWithPauses.nano, it.totalFilesSize)
        }
      }.computeAverageSpeed()
    }

  private fun Collection<Triple<String, Double, JsonFileSize>>.computeAverageSpeed(): Map<String, Int> = groupBy { it.first }.mapValues { entry ->
    JsonProcessingSpeed(entry.value.sumOf { it.third.bytes }, entry.value.sumOf { it.second.toLong() }).toKiloBytesPerSecond()
  }

  private val processingSpeedPerBaseLanguageWorst: Map<String, Int>
    get() {
      return indexingHistories.flatMap { it.totalStatsPerBaseLanguage }.groupBy { it.language }.mapValues {
        it.value.minOf { jsonStatsPerParentLanguage -> jsonStatsPerParentLanguage.totalProcessingSpeed.toKiloBytesPerSecond() }
      }
    }

  private val processingSpeedPerBaseLanguageAvg: Map<String, Int>
    get() {
      return indexingHistories.flatMap { history ->
        history.totalStatsPerBaseLanguage.map {
          Triple(it.language, it.partOfTotalProcessingTime.partition * history.times.totalWallTimeWithPauses.nano, it.totalFilesSize)
        }
      }.computeAverageSpeed()
    }

  private val processingTimePerFileType: Map<String, Long>
    get() {
      val indexingDurationMap = mutableMapOf<String, Long>()
      indexingHistories.forEach { indexingHistory ->
        indexingHistory.totalStatsPerFileType.forEach { totalStatsPerFileType ->
          val duration = (indexingHistory.times.totalWallTimeWithPauses.nano * totalStatsPerFileType.partOfTotalProcessingTime.partition).toLong()
          indexingDurationMap[totalStatsPerFileType.fileType] = indexingDurationMap[totalStatsPerFileType.fileType]?.let { it + duration }
                                                                ?: duration
        }
      }
      return indexingDurationMap
    }

  val slowIndexedFiles: Map<String, List<JsonFileProviderIndexStatistics.JsonSlowIndexedFile>>
    get() {
      val indexedFiles = hashMapOf<String, MutableList<JsonFileProviderIndexStatistics.JsonSlowIndexedFile>>()
      indexingHistories.flatMap { it.fileProviderStatistics }.forEach { fileProviderStatistic ->
        indexedFiles.getOrPut(fileProviderStatistic.providerName) { arrayListOf() } += fileProviderStatistic.slowIndexedFiles
      }
      return indexedFiles
    }

  override fun toString() = buildString {
    appendLine("IndexingMetrics(${ideStartResult.runContext.contextName}):")
    appendLine("IndexingMetrics(")
    for ((name, value) in ideStartResult.mainReportAttributes + toReportTimeAttributes() + toReportCountersAttributes()) {
      appendLine("  $name = $value")
    }
    appendLine(")")
  }

  fun toReportTimeAttributes(): Map<String, String> = mapOf(
    "suspended time" to StringUtil.formatDuration(totalPausedTime),
    "total scan files time" to StringUtil.formatDuration(totalScanFilesTimeWithoutPauses),
    "total indexing time" to StringUtil.formatDuration(totalIndexingTimeWithoutPauses),
    "total updating time" to StringUtil.formatDuration(totalTimeOfScanningOrIndexing),
  )

  fun toReportCountersAttributes(): Map<String, String> = mapOf(
    "number of indexed files" to totalNumberOfIndexedFiles.toString(),
    "number of scanned files" to totalNumberOfScannedFiles.toString(),
    "number of files indexed by extensions" to totalNumberOfFilesFullyIndexedByExtensions.toString(),
    "number of scanning runs" to totalNumberOfRunsOfScanning.toString(),
    "number of indexing runs" to totalNumberOfRunsOfIndexing.toString(),
    "number of full rescannings" to numberOfFullRescanning.toString()
  )

  fun getListOfIndexingMetrics(): List<PerformanceMetrics.Metric> {
    val numberOfIndexedFiles = totalNumberOfIndexedFiles
    val numberOfFilesFullyIndexedByExtensions = totalNumberOfFilesFullyIndexedByExtensions
    return listOf(
      PerformanceMetrics.newDuration("indexingTimeWithoutPauses", durationMillis = totalIndexingTimeWithoutPauses),
      PerformanceMetrics.newDuration("scanningTimeWithoutPauses", durationMillis = totalScanFilesTimeWithoutPauses),
      PerformanceMetrics.newDuration("pausedTimeInIndexingOrScanning", durationMillis = totalPausedTime),
      PerformanceMetrics.newDuration("dumbModeTimeWithPauses", durationMillis = totalDumbModeTimeWithPauses),
      PerformanceMetrics.newCounter("numberOfIndexedFiles", value = numberOfIndexedFiles.toLong()),
      PerformanceMetrics.newCounter("numberOfIndexedFilesWritingIndexValue", value = totalNumberOfIndexedFilesWritingIndexValues.toLong()),
      PerformanceMetrics.newCounter("numberOfFilesIndexedByExtensions", value = numberOfFilesFullyIndexedByExtensions.toLong()),
      PerformanceMetrics.newCounter("numberOfFilesIndexedWithoutExtensions",
                                    value = (numberOfIndexedFiles - numberOfFilesFullyIndexedByExtensions).toLong()),
      PerformanceMetrics.newCounter("numberOfRunsOfScannning", value = totalNumberOfRunsOfScanning.toLong()),
      PerformanceMetrics.newCounter("numberOfRunsOfIndexing", value = totalNumberOfRunsOfIndexing.toLong())
    ) + getProcessingSpeedOfFileTypes(processingSpeedPerFileTypeAvg, "Avg") +
           getProcessingSpeedOfFileTypes(processingSpeedPerFileTypeWorst, "Worst") +
           getProcessingSpeedOfBaseLanguages(processingSpeedPerBaseLanguageAvg, "Avg") +
           getProcessingSpeedOfBaseLanguages(processingSpeedPerBaseLanguageWorst, "Worst") +
           getProcessingTimeOfFileType(processingTimePerFileType) +
           collectPerformanceMetricsFromCSV(ideStartResult, "lexer", "lexing") +
           collectPerformanceMetricsFromCSV(ideStartResult, "parser", "parsing")
  }
}

private fun collectPerformanceMetricsFromCSV(runResult: IDEStartResult,
                                             metricPrefixInCSV: String,
                                             resultingMetricPrefix: String): List<PerformanceMetrics.Metric> {
  val timeRegex = Regex("${metricPrefixInCSV}\\.(.+)\\.time\\.ns")
  val time = StarterTelemetryJsonMeterCollector(MetricsSelectionStrategy.SUM) {
    it.name.startsWith("$metricPrefixInCSV.") && it.name.endsWith(".time.ns")
  }.collect(runResult.runContext).associate {
    val language = timeRegex.find(it.id.name)?.groups?.get(1)?.value
    Pair(language, TimeUnit.NANOSECONDS.toMillis(it.value))
  }
  val sizeRegex = Regex("${metricPrefixInCSV}\\.(.+)\\.size\\.bytes")
  val size = StarterTelemetryJsonMeterCollector(MetricsSelectionStrategy.SUM) {
    it.name.startsWith("$metricPrefixInCSV.") && it.name.endsWith(".size.bytes")
  }.collect(runResult.runContext).associate {
    val language = sizeRegex.find(it.id.name)?.groups?.get(1)?.value
    Pair(language, it.value)
  }
  val speed = time.filter { it.value != 0L }.mapValues {
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
  return IndexingMetrics(startResult, jsonIndexDiagnostics)
}

private fun getProcessingSpeedOfFileTypes(mapFileTypeToSpeed: Map<String, Int>, suffix: String): List<PerformanceMetrics.Metric> =
  mapFileTypeToSpeed.map {
    PerformanceMetrics.newCounter("processingSpeed$suffix#${it.key}", value = it.value.toLong())
  }

private fun getProcessingSpeedOfBaseLanguages(mapBaseLanguageToSpeed: Map<String, Int>, suffix: String): List<PerformanceMetrics.Metric> =
  mapBaseLanguageToSpeed.map {
    PerformanceMetrics.newCounter("processingSpeedOfBaseLanguage$suffix#${it.key}", value = it.value.toLong())
  }

private fun getProcessingTimeOfFileType(mapFileTypeToDuration: Map<String, Long>): List<PerformanceMetrics.Metric> =
  mapFileTypeToDuration.map {
    PerformanceMetrics.newDuration("processingTime#${it.key}", durationMillis = TimeUnit.NANOSECONDS.toMillis(it.value))
  }

data class ScanningStatistics(val numberOfScannedFiles: Long = 0, val numberOfSkippedFiles: Long = 0, val totalSumOfThreadTimesWithPauses: Long = 0) {
  fun merge(scanningStatistics: JsonScanningStatistics): ScanningStatistics {
    return ScanningStatistics(
      numberOfScannedFiles = numberOfScannedFiles + scanningStatistics.numberOfScannedFiles,
      numberOfSkippedFiles = numberOfSkippedFiles + scanningStatistics.numberOfSkippedFiles,
      totalSumOfThreadTimesWithPauses = totalSumOfThreadTimesWithPauses + scanningStatistics.totalOneThreadTimeWithPauses.milliseconds
    )
  }
}