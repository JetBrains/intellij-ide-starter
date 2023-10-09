package com.intellij.tools.ide.metrics.collector.starter.metrics

import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.tools.ide.metrics.collector.collector.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.metrics.createPerformanceMetricCounter
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper
import com.intellij.util.indexing.diagnostic.dto.*
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePath
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.math.max

val metricIndexingTimeWithoutPauses = PerformanceMetrics.MetricId.Duration("indexingTimeWithoutPauses")
val metricScanningTimeWithoutPauses = PerformanceMetrics.MetricId.Duration("scanningTimeWithoutPauses")
val metricPausedTimeInIndexingOrScanning = PerformanceMetrics.MetricId.Duration("pausedTimeInIndexingOrScanning")
val metricDumbModeTimeWithPauses = PerformanceMetrics.MetricId.Duration("dumbModeTimeWithPauses")
val metricNumberOfIndexedFiles = PerformanceMetrics.MetricId.Counter("numberOfIndexedFiles")
val metricNumberOfFilesIndexedByExtensions = PerformanceMetrics.MetricId.Counter("numberOfFilesIndexedByExtensions")
val metricNumberOfFilesIndexedWithoutExtensions = PerformanceMetrics.MetricId.Counter("numberOfFilesIndexedWithoutExtensions")
val metricNumberOfRunsOfScanning = PerformanceMetrics.MetricId.Counter("numberOfRunsOfScannning")
val metricNumberOfRunsOfIndexing = PerformanceMetrics.MetricId.Counter("numberOfRunsOfIndexing")
val metricIds = listOf(metricIndexingTimeWithoutPauses, metricScanningTimeWithoutPauses, metricPausedTimeInIndexingOrScanning,
                       metricDumbModeTimeWithPauses,
                       metricNumberOfIndexedFiles, metricNumberOfFilesIndexedByExtensions, metricNumberOfFilesIndexedWithoutExtensions,
                       metricNumberOfRunsOfScanning, metricNumberOfRunsOfIndexing)

data class IndexingMetrics(
  val ideStartResult: IDEStartResult,
  val jsonIndexDiagnostics: List<JsonIndexingActivityDiagnostic>
) {
  private val scanningHistories: List<JsonProjectScanningHistory>
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

  private val processingSpeedPerFileType: Map<String, Int>
    get() {
      val map = mutableMapOf<String, Int>()
      indexingHistories.flatMap { it.totalStatsPerFileType }.forEach { totalStatsPerFileType ->
        val speed = totalStatsPerFileType.totalProcessingSpeed.toKiloBytesPerSecond()
        if (map.containsKey(totalStatsPerFileType.fileType)) {
          if (map[totalStatsPerFileType.fileType]!! < speed) {
            map[totalStatsPerFileType.fileType] = speed
          }
        }
        else {
          map[totalStatsPerFileType.fileType] = speed
        }
      }
      return map
    }

  private val processingSpeedPerBaseLanguage: Map<String, Int>
    get() {
      val speedMap = mutableMapOf<String, Int>()
      indexingHistories.flatMap { it.totalStatsPerBaseLanguage }.forEach { totalStatsPerLanguage ->
        val speed = totalStatsPerLanguage.totalProcessingSpeed.toKiloBytesPerSecond()
        speedMap[totalStatsPerLanguage.language] = speedMap[totalStatsPerLanguage.language]?.let { max(speed, it) } ?: speed
      }
      return speedMap
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
      PerformanceMetrics.Metric(metricIndexingTimeWithoutPauses, value = totalIndexingTimeWithoutPauses),
      PerformanceMetrics.Metric(metricScanningTimeWithoutPauses, value = totalScanFilesTimeWithoutPauses),
      PerformanceMetrics.Metric(metricPausedTimeInIndexingOrScanning, value = totalPausedTime),
      PerformanceMetrics.Metric(metricDumbModeTimeWithPauses, value = totalDumbModeTimeWithPauses),
      PerformanceMetrics.Metric(metricNumberOfIndexedFiles, value = numberOfIndexedFiles.toLong()),
      PerformanceMetrics.Metric(metricNumberOfFilesIndexedByExtensions, value = numberOfFilesFullyIndexedByExtensions.toLong()),
      PerformanceMetrics.Metric(metricNumberOfFilesIndexedWithoutExtensions,
                                value = (numberOfIndexedFiles - numberOfFilesFullyIndexedByExtensions).toLong()),
      PerformanceMetrics.Metric(metricNumberOfRunsOfScanning, value = totalNumberOfRunsOfScanning.toLong()),
      PerformanceMetrics.Metric(metricNumberOfRunsOfIndexing, value = totalNumberOfRunsOfIndexing.toLong())
    ) + getProcessingSpeedOfFileTypes(processingSpeedPerFileType) + getProcessingSpeedOfBaseLanguages(processingSpeedPerBaseLanguage)
  }
}

fun extractIndexingMetrics(startResult: IDEStartResult): IndexingMetrics {
  val indexDiagnosticDirectory = startResult.runContext.logsDir / "indexing-diagnostic"
  val indexDiagnosticDirectoryChildren = Files.list(indexDiagnosticDirectory).filter { it.toFile().isDirectory }.use { it.toList() }
  val projectIndexDiagnosticDirectory = indexDiagnosticDirectoryChildren.let { perProjectDirs ->
    perProjectDirs.singleOrNull() ?: error("Only one project diagnostic dir is expected: ${perProjectDirs.joinToString()}")
  }
  val jsonIndexDiagnostics = Files.list(projectIndexDiagnosticDirectory)
    .use { stream -> stream.filter { it.extension == "json" }.toList() }
    .filter { Files.size(it) > 0L }
    .mapNotNull { IndexDiagnosticDumper.readJsonIndexingActivityDiagnostic(it) }
  return IndexingMetrics(startResult, jsonIndexDiagnostics)
}

private fun getProcessingSpeedOfFileTypes(mapFileTypeToSpeed: Map<String, Int>): List<PerformanceMetrics.Metric> {
  val list = mutableListOf<PerformanceMetrics.Metric>()
  mapFileTypeToSpeed.forEach {
    list.add(PerformanceMetrics.Metric("processingSpeed#${it.key}".createPerformanceMetricCounter(), value = it.value.toLong()))
  }
  return list
}

private fun getProcessingSpeedOfBaseLanguages(mapBaseLanguageToSpeed: Map<String, Int>): List<PerformanceMetrics.Metric> =
  mapBaseLanguageToSpeed.map {
    PerformanceMetrics.Metric("processingSpeedOfBaseLanguage#${it.key}".createPerformanceMetricCounter(), value = it.value.toLong())
  }

data class ScanningStatistics(val numberOfScannedFiles: Long = 0, val numberOfSkippedFiles: Long = 0, val totalSumOfThreadTimesWithPauses: Long = 0) {
  fun merge(scanningStatistics: JsonScanningStatistics) : ScanningStatistics {
    return ScanningStatistics(
      numberOfScannedFiles = numberOfScannedFiles + scanningStatistics.numberOfScannedFiles,
      numberOfSkippedFiles = numberOfSkippedFiles + scanningStatistics.numberOfSkippedFiles,
      totalSumOfThreadTimesWithPauses = totalSumOfThreadTimesWithPauses + scanningStatistics.totalOneThreadTimeWithPauses.milliseconds
    )
  }
}