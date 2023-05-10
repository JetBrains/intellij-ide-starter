package com.intellij.metricsCollector.metrics

import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.metricsCollector.collector.PerformanceMetrics
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper
import com.intellij.util.indexing.diagnostic.dto.JsonFileProviderIndexStatistics
import com.intellij.util.indexing.diagnostic.dto.JsonIndexDiagnostic
import com.intellij.util.indexing.diagnostic.dto.JsonScanningStatistics
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePath
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.io.path.div
import kotlin.io.path.extension

val metricIndexing = PerformanceMetrics.MetricId.Duration("indexing")
val metricScanning = PerformanceMetrics.MetricId.Duration("scanning")
val metricUpdatingTime = PerformanceMetrics.MetricId.Duration("updatingTime")
val metricNumberOfIndexedFiles = PerformanceMetrics.MetricId.Counter("numberOfIndexedFiles")
val metricNumberOfFilesIndexedByExtensions = PerformanceMetrics.MetricId.Counter("numberOfFilesIndexedByExtensions")
val metricNumberOfIndexingRuns = PerformanceMetrics.MetricId.Counter("numberOfIndexingRuns")
val metricIds = listOf(metricIndexing, metricScanning, metricNumberOfIndexedFiles, metricNumberOfFilesIndexedByExtensions,
                       metricNumberOfIndexingRuns)


data class OldIndexingMetrics(
  val ideStartResult: IDEStartResult,
  val jsonIndexDiagnostics: List<JsonIndexDiagnostic>
) {

  val totalNumberOfIndexingRuns: Int
    get() = jsonIndexDiagnostics.count { it.projectIndexingHistory.projectName.isNotEmpty() }

  val totalUpdatingTime: Long
    get() = jsonIndexDiagnostics.map { TimeUnit.NANOSECONDS.toMillis(it.projectIndexingHistory.times.totalUpdatingTime.nano) }.sum()

  val totalIndexingTime: Long
    get() = jsonIndexDiagnostics.map { TimeUnit.NANOSECONDS.toMillis(it.projectIndexingHistory.times.indexingTime.nano) }.sum()

  val totalScanFilesTime: Long
    get() = jsonIndexDiagnostics.map { TimeUnit.NANOSECONDS.toMillis(it.projectIndexingHistory.times.scanFilesTime.nano) }.sum()

  @Suppress("unused")
  val totalPushPropertiesTime: Long
    get() = jsonIndexDiagnostics.map { TimeUnit.NANOSECONDS.toMillis(it.projectIndexingHistory.times.pushPropertiesTime.nano) }.sum()

  private val suspendedTime: Long
    get() = jsonIndexDiagnostics.map { TimeUnit.NANOSECONDS.toMillis(it.projectIndexingHistory.times.totalSuspendedTime.nano) }.sum()

  val totalNumberOfIndexedFiles: Int
    get() = jsonIndexDiagnostics.sumOf { diagnostic ->
      diagnostic.projectIndexingHistory.fileProviderStatistics.sumOf { it.totalNumberOfIndexedFiles }
    }

  val totalNumberOfScannedFiles: Int
    get() = jsonIndexDiagnostics.sumOf { diagnostic ->
      diagnostic.projectIndexingHistory.scanningStatistics.sumOf { it.numberOfScannedFiles }
    }

  val totalNumberOfFilesFullyIndexedByExtensions: Int
    get() = jsonIndexDiagnostics.map { it.projectIndexingHistory.fileProviderStatistics.map { provider -> provider.totalNumberOfFilesFullyIndexedByExtensions }.sum() }.sum() +
            jsonIndexDiagnostics.map { it.projectIndexingHistory.scanningStatistics.map { scan -> scan.numberOfFilesFullyIndexedByInfrastructureExtensions }.sum() }.sum()

  val listOfFilesFullyIndexedByExtensions: List<String>
    get() {
      val indexedFiles = mutableListOf<String>()
      for (jsonIndexDiagnostic in jsonIndexDiagnostics) {
        for (fileProviderStatistic in jsonIndexDiagnostic.projectIndexingHistory.fileProviderStatistics) {
          indexedFiles.addAll(fileProviderStatistic.filesFullyIndexedByExtensions)
        }
      }
      for (jsonIndexDiagnostic in jsonIndexDiagnostics) {
        for (scanningStatistic in jsonIndexDiagnostic.projectIndexingHistory.scanningStatistics) {
          indexedFiles.addAll(scanningStatistic.filesFullyIndexedByInfrastructureExtensions)
        }
      }
      return indexedFiles.distinct()
    }

  val numberOfIndexedByExtensionsFilesForEachProvider: Map<String, Int>
    get() {
      val indexedByExtensionsFiles = mutableMapOf<String /* Provider name */, Int /* Number of files indexed by extensions */>()
      for (jsonIndexDiagnostic in jsonIndexDiagnostics) {
        for (scanStat in jsonIndexDiagnostic.projectIndexingHistory.scanningStatistics) {
          indexedByExtensionsFiles[scanStat.providerName] = indexedByExtensionsFiles.getOrDefault(scanStat.providerName,
                                                                                                  0) + scanStat.numberOfFilesFullyIndexedByInfrastructureExtensions
        }
      }
      return indexedByExtensionsFiles
    }

  val numberOfIndexedFilesByUsualIndexesPerProvider: Map<String, Int>
    get() {
      val indexedFiles = mutableMapOf<String /* Provider name */, Int /* Number of files indexed by usual indexes */>()
      for (jsonIndexDiagnostic in jsonIndexDiagnostics) {
        for (indexStats in jsonIndexDiagnostic.projectIndexingHistory.fileProviderStatistics) {
          indexedFiles[indexStats.providerName] = indexedFiles.getOrDefault(indexStats.providerName,
                                                                            0) + indexStats.totalNumberOfIndexedFiles
        }
      }
      return indexedFiles
    }

  val scanningStatisticsByProviders: Map<String, ScanningStatistics>
    get() {
      val indexedFiles = mutableMapOf<String /* Provider name */, ScanningStatistics>()
      for (jsonIndexDiagnostic in jsonIndexDiagnostics) {
        for (indexStats in jsonIndexDiagnostic.projectIndexingHistory.scanningStatistics) {
          val value: ScanningStatistics = indexedFiles.getOrDefault(indexStats.providerName, ScanningStatistics())
          indexedFiles[indexStats.providerName] = value.merge(indexStats)
        }
      }
      return indexedFiles
    }

  val numberOfFullRescanning: Int
    get() = jsonIndexDiagnostics.count {
      it.projectIndexingHistory.times.scanningType.isFull &&
      it.projectIndexingHistory.projectName.isNotEmpty()
    }

  val allIndexedFiles: Map<String, List<PortableFilePath>>
    get() {
      val indexedFiles = hashMapOf<String /* Provider name */, MutableList<PortableFilePath>>()
      for (jsonIndexDiagnostic in jsonIndexDiagnostics) {
        for (fileProviderStatistic in jsonIndexDiagnostic.projectIndexingHistory.fileProviderStatistics) {
          indexedFiles.getOrPut(fileProviderStatistic.providerName) { arrayListOf() } +=
            fileProviderStatistic.indexedFiles.orEmpty().map { it.path }
        }
      }
      return indexedFiles
    }

  private val processingSpeedPerFileType: Map<String, Int>
    get() {
      val map = mutableMapOf<String, Int>()
      for (jsonIndexDiagnostic in jsonIndexDiagnostics) {
        for (totalStatsPerFileType in jsonIndexDiagnostic.projectIndexingHistory.totalStatsPerFileType) {
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
      }
      return map
    }

  val slowIndexedFiles: Map<String, List<JsonFileProviderIndexStatistics.JsonSlowIndexedFile>>
    get() {
      val indexedFiles = hashMapOf<String, MutableList<JsonFileProviderIndexStatistics.JsonSlowIndexedFile>>()
      jsonIndexDiagnostics.forEach { jsonIndexDiagnostic ->
        jsonIndexDiagnostic.projectIndexingHistory.fileProviderStatistics.forEach { fileProviderStatistic ->
          indexedFiles.getOrPut(fileProviderStatistic.providerName) { arrayListOf() } += fileProviderStatistic.slowIndexedFiles
        }
      }
      return indexedFiles
    }

  override fun toString() = buildString {
    appendLine("OldIndexingMetrics(${ideStartResult.runContext.contextName}):")
    appendLine("OldIndexingMetrics(")
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
    "number of indexing runs" to totalNumberOfIndexingRuns.toString(),
    "number of full indexing" to numberOfFullRescanning.toString()
  )

  fun getListOfIndexingMetrics(): List<PerformanceMetrics.Metric<out Number>> {
    return listOf(
      PerformanceMetrics.Metric(metricIndexing, value = totalIndexingTime),
      PerformanceMetrics.Metric(metricScanning, value = totalScanFilesTime),
      PerformanceMetrics.Metric(metricUpdatingTime, value = totalUpdatingTime),
      PerformanceMetrics.Metric(metricNumberOfIndexedFiles, value = totalNumberOfIndexedFiles),
      PerformanceMetrics.Metric(metricNumberOfFilesIndexedByExtensions, value = totalNumberOfFilesFullyIndexedByExtensions),
      PerformanceMetrics.Metric(metricNumberOfIndexingRuns, value = totalNumberOfIndexingRuns)
    ) + getProcessingSpeedOfFileTypes(processingSpeedPerFileType)
  }
}

fun extractOldIndexingMetrics(startResult: IDEStartResult): OldIndexingMetrics {
  val indexDiagnosticDirectory = startResult.context.paths.logsDir / "old-version-indexing-diagnostic"
  val indexDiagnosticDirectoryChildren = Files.list(indexDiagnosticDirectory).filter { it.toFile().isDirectory }.use { it.toList() }
  val projectIndexDiagnosticDirectory = indexDiagnosticDirectoryChildren.let { perProjectDirs ->
    perProjectDirs.singleOrNull() ?: error("Only one project diagnostic dir is expected: ${perProjectDirs.joinToString()}")
  }
  val jsonIndexDiagnostics = Files.list(projectIndexDiagnosticDirectory)
    .use { stream -> stream.filter { it.extension == "json" }.toList() }
    .filter { Files.size(it) > 0L }
    .map { IndexDiagnosticDumper.readJsonIndexDiagnostic(it) }
  return OldIndexingMetrics(startResult, jsonIndexDiagnostics)
}

private fun getProcessingSpeedOfFileTypes(mapFileTypeToSpeed: Map<String, Int>): List<PerformanceMetrics.Metric<*>> {
    val list = mutableListOf<PerformanceMetrics.Metric<*>>()
  mapFileTypeToSpeed.forEach{
       list.add(PerformanceMetrics.Metric("processingSpeed#${it.key}".createPerformanceMetricCounter(), value = it.value))
      }
    return list
}

data class ScanningStatistics(val numberOfScannedFiles: Int = 0, val numberOfSkippedFiles: Int = 0, val scanningTime: Long = 0) {
  fun merge(scanningStatistics: JsonScanningStatistics) : ScanningStatistics {
    return ScanningStatistics(
      numberOfScannedFiles = numberOfScannedFiles + scanningStatistics.numberOfScannedFiles,
      numberOfSkippedFiles = numberOfSkippedFiles + scanningStatistics.numberOfSkippedFiles,  
      scanningTime = scanningTime + scanningStatistics.scanningTime.milliseconds
    )
  }
}