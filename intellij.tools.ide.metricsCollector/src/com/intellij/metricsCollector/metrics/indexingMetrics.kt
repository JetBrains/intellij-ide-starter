package com.intellij.metricsCollector.metrics

import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.metricsCollector.collector.PerformanceMetrics
import com.intellij.metricsCollector.collector.PerformanceMetricsDto
import com.intellij.metricsCollector.publishing.ApplicationMetricDto
import com.intellij.metricsCollector.publishing.toPerformanceMetricsJson
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper
import com.intellij.util.indexing.diagnostic.dto.*
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePath
import java.nio.file.Files
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.BiFunction
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.math.abs
import kotlin.reflect.KProperty1


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
    appendLine("IndexingMetrics(${ideStartResult.runContext.contextName}):")
    appendLine("IndexingMetrics(")
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

fun extractIndexingMetrics(startResult: IDEStartResult): IndexingMetrics {
  val indexDiagnosticDirectory = startResult.context.paths.logsDir / "indexing-diagnostic"
  val indexDiagnosticDirectoryChildren = Files.list(indexDiagnosticDirectory).filter { it.toFile().isDirectory }.use { it.toList() }
  val projectIndexDiagnosticDirectory = indexDiagnosticDirectoryChildren.let { perProjectDirs ->
    perProjectDirs.singleOrNull() ?: error("Only one project diagnostic dir is expected: ${perProjectDirs.joinToString()}")
  }
  val jsonIndexDiagnostics = Files.list(projectIndexDiagnosticDirectory)
    .use { stream -> stream.filter { it.extension == "json" }.toList() }
    .filter { Files.size(it) > 0L }
    .mapNotNull { IndexDiagnosticDumper.readJsonIndexingActivityDiagnostic(it) }

  val metrics = IndexingMetrics(startResult, jsonIndexDiagnostics)
  val oldMetrics = extractOldIndexingMetrics(startResult)
  compareOldAndCurrentMetrics(oldMetrics, metrics)

  val json = metrics.toPerformanceMetricsJson()
  val oldJson = oldMetrics.toPerformanceMetricsJson()
  compareOldAndCurrentJsonMetrics(oldJson, json)

  return metrics
}

private fun compareOldAndCurrentMetrics(oldMetrics: OldIndexingMetrics,
                                        metrics: IndexingMetrics) {
  val comparisonMessage = buildString {
    fun <T> check(oldValue: T, value: T, name: String) {
      val equalityCheck: BiFunction<T, T, Boolean> =
        when (name) {
          "totalIndexingTime" -> {
            BiFunction { t, u -> abs((t as Long) - (u as Long)) < 1000 }
          }
          "totalUpdatingTime" -> {
            BiFunction { t, u -> abs((t as Long) - (u as Long)) < 600 }
          }
          else -> {
            BiFunction { t, u -> t == u }
          }
        }
      if (!equalityCheck.apply(oldValue, value)) {
        appendLine("$name property differs: ${oldValue} and ${value}")
      }
    }

    check(oldMetrics.totalNumberOfIndexingRuns, metrics.totalNumberOfIndexActivitiesRuns, "totalNumberOfIndexingRuns")
    check(oldMetrics.totalUpdatingTime, metrics.totalUpdatingTime, "totalUpdatingTime")
    check(oldMetrics.totalIndexingTime, metrics.totalIndexingTime, "totalIndexingTime")
    check(oldMetrics.totalScanFilesTime, metrics.totalScanFilesTime, "totalScanFilesTime")
    check(oldMetrics.totalPushPropertiesTime, metrics.totalDelayedFilesPushTime, "totalDelayedFilesPushTime")
    check(oldMetrics.totalNumberOfIndexedFiles, metrics.totalNumberOfIndexedFiles, "totalNumberOfIndexedFiles")
    check(oldMetrics.totalNumberOfScannedFiles, metrics.totalNumberOfScannedFiles, "totalNumberOfScannedFiles")
    check(oldMetrics.totalNumberOfFilesFullyIndexedByExtensions, metrics.totalNumberOfFilesFullyIndexedByExtensions,
          "totalNumberOfFilesFullyIndexedByExtensions")
    check(oldMetrics.numberOfIndexedByExtensionsFilesForEachProvider, metrics.numberOfIndexedByExtensionsFilesForEachProvider,
          "numberOfIndexedByExtensionsFilesForEachProvider")
    check(oldMetrics.numberOfIndexedFilesByUsualIndexesPerProvider, metrics.numberOfIndexedFilesByUsualIndexesPerProvider,
          "numberOfIndexedFilesByUsualIndexesPerProvider")
    check(oldMetrics.scanningStatisticsByProviders, metrics.scanningStatisticsByProviders, "scanningStatisticsByProviders")
    check(oldMetrics.numberOfFullRescanning, metrics.numberOfFullRescanning, "numberOfFullRescanning")
    check(orderAllIndexedFiles(oldMetrics.allIndexedFiles), orderAllIndexedFiles(metrics.allIndexedFiles), "allIndexedFiles")
    check(orderSlowIndexedFiles(oldMetrics.slowIndexedFiles), orderSlowIndexedFiles(metrics.slowIndexedFiles), "slowIndexedFiles")
  }
  checkMessage(comparisonMessage, oldMetrics, metrics)
}

private fun orderSlowIndexedFiles(slowIndexedFiles: Map<String, List<JsonFileProviderIndexStatistics.JsonSlowIndexedFile>>):
  Map<String, List<JsonFileProviderIndexStatistics.JsonSlowIndexedFile>> =
  slowIndexedFiles.mapValues { entry ->
    entry.value.sortedWith { file1, file2 ->
      if (file1.fileName != file2.fileName) return@sortedWith file1.fileName.compareTo(file2.fileName)
      if (file1.processingTime != file2.processingTime) return@sortedWith file1.processingTime.nano.compareTo(file2.processingTime.nano)
      if (file1.contentLoadingTime != file2.contentLoadingTime) return@sortedWith file1.contentLoadingTime.nano.compareTo(
        file2.contentLoadingTime.nano)
      return@sortedWith file1.evaluationOfIndexValueChangerTime.nano.compareTo(file2.evaluationOfIndexValueChangerTime.nano)
    }
  }.toSortedMap()

private fun orderAllIndexedFiles(allIndexedFiles: Map<String, List<PortableFilePath>>): Map<String, List<PortableFilePath>> =
  allIndexedFiles.mapValues { entry -> entry.value.sortedBy(PortableFilePath::presentablePath) }.toSortedMap()

private fun <T> checkMessage(comparisonMessage: String,
                             oldMetrics: T,
                             metrics: T) {
  check(comparisonMessage.isEmpty()) {
    buildString {
      appendLine(comparisonMessage)
      appendLine("Old metrics and current metrics differ")
      appendLine("Old metrics:")
      appendLine(oldMetrics)
      appendLine("\nCurrent metrics:")
      appendLine(metrics)
    }
  }
}

private fun compareOldAndCurrentJsonMetrics(oldJson: PerformanceMetricsDto,
                                            currentJson: PerformanceMetricsDto) {
  val comparisonMessage = buildString {
    fun check(value: KProperty1<PerformanceMetricsDto, String>, name: String) {
      if (value(oldJson) != value(currentJson)) {
        appendLine("$name property differs: ${value(oldJson)} and ${value(currentJson)}")
      }
    }
    //check(PerformanceMetricsDto::generated, "generated")
    check(PerformanceMetricsDto::project, "project")
    check(PerformanceMetricsDto::os, "os")
    check(PerformanceMetricsDto::osFamily, "osFamily")
    check(PerformanceMetricsDto::runtime, "runtime")
    check(PerformanceMetricsDto::build, "build")
    //check(PerformanceMetricsDto::buildDate, "buildDate")
    check(PerformanceMetricsDto::productCode, "productCode")

    if (oldJson.metrics.size != currentJson.metrics.size) {
      appendLine("Different size of metrics: ${oldJson.metrics.size} and ${currentJson.metrics.size} ")
    }

    fun check(oldMetric: ApplicationMetricDto,
              currentMetric: ApplicationMetricDto,
              metricName: String,
              value: KProperty1<ApplicationMetricDto, Long?>,
              name: String,
              equalityCheck: BiFunction<Long?, Long?, Boolean>) {
      if (!equalityCheck.apply(value(oldMetric), value(currentMetric))) {
        appendLine("Metric $name differs in $metricName: ${value(oldMetric)} and ${value(currentMetric)}")
      }
    }

    for (metric in oldJson.metrics) {
      val name = metric.n
      val currentMetric = currentJson.metrics.firstOrNull { it.n == name }
      if (currentMetric == null) {
        appendLine("Metric ${name} not found")
        continue
      }

      val equalityCheck: BiFunction<Long?, Long?, Boolean>
      if ("indexing" == name || "updatingTime" == name) {
        equalityCheck = BiFunction { t, u ->
          when (t == null) {
            true -> u == null
            false -> u != null && abs(t - u) < 600
          }
        }
      }
      else {
        equalityCheck = BiFunction { t, u -> Objects.equals(t, u) }
      }

      check(metric, currentMetric, name, ApplicationMetricDto::c, "c", equalityCheck)
      check(metric, currentMetric, name, ApplicationMetricDto::d, "d", equalityCheck)
      check(metric, currentMetric, name, ApplicationMetricDto::v, "v", equalityCheck)
    }
  }

  checkMessage(comparisonMessage, oldJson, currentJson)
}

private fun getProcessingSpeedOfFileTypes(mapFileTypeToSpeed: Map<String, Int>): List<PerformanceMetrics.Metric<*>> {
  val list = mutableListOf<PerformanceMetrics.Metric<*>>()
  mapFileTypeToSpeed.forEach {
    list.add(PerformanceMetrics.Metric("processingSpeed#${it.key}".createPerformanceMetricCounter(), value = it.value))
  }
  return list
}