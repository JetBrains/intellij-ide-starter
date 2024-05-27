package com.intellij.tools.ide.metrics.collector.starter.metrics

import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.starter.collector.StarterTelemetryJsonMeterCollector
import com.intellij.tools.ide.util.common.logError

object CommonMetrics {
  private fun Number.convertNsToMs(): Long {
    return this.toLong() / 1_000_000
  }

  fun getAwtMetrics(startResult: IDEStartResult): List<PerformanceMetrics.Metric> {
    try {
      return StarterTelemetryJsonMeterCollector(MetricsSelectionStrategy.LATEST) {
        it.name == "AWTEventQueue.dispatchTimeTotalNS"
      }.collect(startResult.runContext).map {
        PerformanceMetrics.Metric.newDuration("AWTEventQueue.dispatchTimeTotal", it.value.convertNsToMs())
      }
    }
    catch (e: Exception) {
      logError("Collecting AWT metrics: ${e.message}")
    }
    return emptyList()
  }

  fun getWriteActionMetrics(startResult: IDEStartResult): List<PerformanceMetrics.Metric> {
    val metricsToStrategy = mapOf("writeAction.count" to MetricsSelectionStrategy.SUM,
                                  "writeAction.wait.ms" to MetricsSelectionStrategy.SUM,
                                  "writeAction.max.wait.ms" to MetricsSelectionStrategy.MAXIMUM,
                                  "writeAction.median.wait.ms" to MetricsSelectionStrategy.LATEST)
    try {
      return metricsToStrategy.flatMap { (metricName, strategy) ->
        StarterTelemetryJsonMeterCollector(strategy) { it.name.startsWith(metricName) }
          .collect(startResult.runContext).map {
            PerformanceMetrics.Metric.newCounter(metricName, it.value)
          }
      }
    }
    catch (e: Exception) {
      logError("Collecting Write Action metrics: ${e.message}")
    }
    return emptyList()
  }

  fun getJvmMetrics(startResult: IDEStartResult,
                    metricsStrategies: Map<String, MetricsSelectionStrategy>
                    = mapOf("JVM.GC.collections" to MetricsSelectionStrategy.SUM,
                            "JVM.GC.collectionTimesMs" to MetricsSelectionStrategy.SUM,
                            "JVM.totalCpuTimeMs" to MetricsSelectionStrategy.SUM,
                            "JVM.maxHeapBytes" to MetricsSelectionStrategy.MAXIMUM,
                            "JVM.maxThreadCount" to MetricsSelectionStrategy.MAXIMUM,
                            "JVM.totalTimeToSafepointsMs" to MetricsSelectionStrategy.SUM)
  ): List<PerformanceMetrics.Metric> {
    try {
      return metricsStrategies.flatMap { (metricName, strategy) ->
        StarterTelemetryJsonMeterCollector(strategy) { it.name.startsWith(metricName) }.collect(startResult.runContext).map {
          val value = if (it.id.name.contains("Bytes")) it.value / 1_000_000 else it.value
          val name = if (it.id.name.contains("Bytes")) it.id.name.replace("Bytes", "Megabytes") else it.id.name
          if (it.id.name.contains("Time")) {
            PerformanceMetrics.Metric.newDuration(it.id.name, it.value)
          }
          else {
            PerformanceMetrics.Metric.newCounter(name, value)
          }
        }
      }
    }
    catch (e: Exception) {
      logError("Collecting JVM metrics: ${e.message}")
    }
    return emptyList()
  }
}