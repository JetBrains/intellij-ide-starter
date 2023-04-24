package com.intellij.metricsCollector.metrics

import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.metricsCollector.collector.PerformanceMetrics
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper
import com.intellij.util.indexing.diagnostic.dto.*
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePath
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.io.path.div
import kotlin.io.path.extension


data class IndexingMetrics(
  val ideStartResult: IDEStartResult,
  val jsonIndexDiagnostics: List<JsonIndexingActivityDiagnostic>
) {
  private val scanningHistories: List<JsonProjectScanningHistory>
    get() = jsonIndexDiagnostics.map { it.projectIndexingActivityHistory }.filterIsInstance<JsonProjectScanningHistory>()
  private val indexingHistories: List<JsonProjectDumbIndexingHistory>
    get() = jsonIndexDiagnostics.map { it.projectIndexingActivityHistory }.filterIsInstance<JsonProjectDumbIndexingHistory>()
  private val scanningStatistics: List<JsonScanningStatistics>
    get() = jsonIndexDiagnostics.map { it.projectIndexingActivityHistory }.flatMap { history ->
      when (history) {
        is JsonProjectScanningHistory -> history.scanningStatistics
        is JsonProjectDumbIndexingHistory -> listOf(history.scanningStatisticsOfRefreshedFiles)
      }
    }

  val totalNumberOfIndexActivitiesRuns: Int
    get() = jsonIndexDiagnostics.count {
      //todo[lene] metrics should be renamed to index activities runs; better count scannings in tests as they are more reproducible
      it.projectIndexingActivityHistory.projectName.isNotEmpty()
    }

  val totalUpdatingTime: Long
    get() = jsonIndexDiagnostics.sumOf {
      TimeUnit.NANOSECONDS.toMillis(it.projectIndexingActivityHistory.times.totalWallTimeWithPauses.nano)
    }

  //todo[lene] definitely useful to track in tests
  val totalDumbModeTime: Long
    get() = jsonIndexDiagnostics.sumOf {
      TimeUnit.NANOSECONDS.toMillis(it.projectIndexingActivityHistory.times.dumbWallTimeWithPauses.nano)
    }

  val totalIndexingTime: Long
    get() = indexingHistories.sumOf { TimeUnit.NANOSECONDS.toMillis(it.times.totalWallTimeWithoutPauses.nano) }

  val totalScanFilesTime: Long
    get() = scanningHistories.sumOf {
      //todo[lene] should actually be TimeUnit.NANOSECONDS.toMillis(it.times.totalWallTimeWithoutPauses.nano) Better to rename the metric
      TimeUnit.NANOSECONDS.toMillis(it.times.collectingIndexableFilesTime.nano)
    }
  val totalDelayedFilesPushTime: Long
    get() = scanningHistories.sumOf {
      TimeUnit.NANOSECONDS.toMillis(it.times.delayedPushPropertiesStageTime.nano)
    }

  private val suspendedTime: Long
    get() = jsonIndexDiagnostics.sumOf { TimeUnit.NANOSECONDS.toMillis(it.projectIndexingActivityHistory.times.wallTimeOnPause.nano) }

  val totalNumberOfIndexedFiles: Int
    get() = indexingHistories.sumOf { history -> history.fileProviderStatistics.sumOf { it.totalNumberOfIndexedFiles } }

  val totalNumberOfScannedFiles: Int
    get() = scanningStatistics.sumOf { it.numberOfScannedFiles }

  val totalNumberOfFilesFullyIndexedByExtensions: Int
    get() = jsonIndexDiagnostics.sumOf {
      when (val fileCount = it.projectIndexingActivityHistory.fileCount) {
        is JsonProjectScanningFileCount -> fileCount.numberOfFilesIndexedByInfrastructureExtensionsDuringScan
        is JsonProjectDumbIndexingFileCount -> fileCount.numberOfRefreshedFilesIndexedByInfrastructureExtensionsDuringScan +
                                               fileCount.numberOfFilesIndexedByInfrastructureExtensionsDuringIndexingStage
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
            indexedFiles.addAll(history.scanningStatisticsOfRefreshedFiles.filesFullyIndexedByInfrastructureExtensions)
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
        val speed = (totalStatsPerFileType.totalProcessingSpeed.totalBytes.toDouble() * 0.001 /
                     totalStatsPerFileType.totalProcessingSpeed.totalTime * TimeUnit.SECONDS.toNanos(1).toDouble()).toInt()
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

  val slowIndexedFiles: Map<String, List<JsonFileProviderIndexStatistics.JsonSlowIndexedFile>>
    get() {
      val indexedFiles = hashMapOf<String, MutableList<JsonFileProviderIndexStatistics.JsonSlowIndexedFile>>()
      indexingHistories.flatMap { it.fileProviderStatistics }.forEach { fileProviderStatistic ->
        indexedFiles.getOrPut(fileProviderStatistic.providerName) { arrayListOf() } += fileProviderStatistic.slowIndexedFiles
      }
      return indexedFiles
    }

  override fun toString() = buildString {
    appendLine("AlternativeIndexingMetrics(${ideStartResult.runContext.contextName}):")
    appendLine("AlternativeIndexingMetrics(")
    for ((name, value) in ideStartResult.mainReportAttributes + toReportTimeAttributes() + toReportCountersAttributes()) {
      appendLine("  $name = $value")
    }
    appendLine(")")
  }

  fun toReportTimeAttributes(): Map<String, String> = mapOf(
    "suspended time" to StringUtil.formatDuration(suspendedTime),
    "total scan files time" to StringUtil.formatDuration(totalScanFilesTime),
    "total indexing time" to StringUtil.formatDuration(totalIndexingTime),
    "total updating time" to StringUtil.formatDuration(totalUpdatingTime),
  )

  fun toReportCountersAttributes(): Map<String, String> = mapOf(
    "number of indexed files" to totalNumberOfIndexedFiles.toString(),
    "number of scanned files" to totalNumberOfScannedFiles.toString(),
    "number of files indexed by extensions" to totalNumberOfFilesFullyIndexedByExtensions.toString(),
    "number of indexing runs" to totalNumberOfIndexActivitiesRuns.toString(),
    "number of full indexing" to numberOfFullRescanning.toString()
  )

  fun getListOfIndexingMetrics(): List<PerformanceMetrics.Metric<out Number>> {
    return listOf(
      PerformanceMetrics.Metric(metricIndexing, value = totalIndexingTime),
      PerformanceMetrics.Metric(metricScanning, value = totalScanFilesTime),
      PerformanceMetrics.Metric(metricUpdatingTime, value = totalUpdatingTime),
      PerformanceMetrics.Metric(metricNumberOfIndexedFiles, value = totalNumberOfIndexedFiles),
      PerformanceMetrics.Metric(metricNumberOfFilesIndexedByExtensions, value = totalNumberOfFilesFullyIndexedByExtensions),
      PerformanceMetrics.Metric(metricNumberOfIndexingRuns, value = totalNumberOfIndexActivitiesRuns)
    ) + getProcessingSpeedOfFileTypes(processingSpeedPerFileType)
  }
}

fun extractAlternativeIndexingMetrics(startResult: IDEStartResult): IndexingMetrics {
  val indexDiagnosticDirectory = startResult.context.paths.logsDir / "indexing-diagnostic"
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

private fun getProcessingSpeedOfFileTypes(mapFileTypeToSpeed: Map<String, Int>): List<PerformanceMetrics.Metric<*>> {
  val list = mutableListOf<PerformanceMetrics.Metric<*>>()
  mapFileTypeToSpeed.forEach {
    list.add(PerformanceMetrics.Metric("processingSpeed#${it.key}".createPerformanceMetricCounter(), value = it.value))
  }
  return list
}