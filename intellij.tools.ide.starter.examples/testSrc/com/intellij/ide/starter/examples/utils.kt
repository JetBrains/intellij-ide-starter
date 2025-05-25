package com.intellij.ide.starter.examples

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.tools.ide.metrics.collector.OpenTelemetrySpanCollector
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics.Metric
import com.intellij.tools.ide.metrics.collector.starter.collector.StarterTelemetrySpanCollector
import com.intellij.tools.ide.metrics.collector.telemetry.SpanFilter
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.bufferedWriter
import kotlin.io.path.copyToRecursively
import kotlin.io.path.div

fun writeMetricsToCSV(results: IDEStartResult, metrics: List<Metric>): Path {
  val resultCsv = results.runContext.reportsDir / "result.csv"
  println("#".repeat(20))
  println("Storing metrics to CSV")
  resultCsv.bufferedWriter().use { writer ->
    metrics.forEach { metric ->
      writer.write(metric.id.name + "," + metric.value)
      println("${metric.id.name}: ${metric.value}")
      writer.newLine()
    }
  }
  println("Result CSV is written to: file://${resultCsv.absolutePathString()}")
  println("#".repeat(20))

  println("Snapshots can be found at: file://" + results.runContext.snapshotsDir)

  return resultCsv
}

fun getMetricsFromSpanAndChildren(ideStartResult: IDEStartResult, spanFilter: SpanFilter): List<Metric> {
  return StarterTelemetrySpanCollector(spanFilter).collect(ideStartResult.runContext)
}

@OptIn(ExperimentalPathApi::class)
fun IDETestContext.copyExistingConfig(configPath: Path): IDETestContext {
  configPath.copyToRecursively(paths.configDir, followLinks = false)
  return this
}

@OptIn(ExperimentalPathApi::class)
fun IDETestContext.copyExistingPlugins(pluginPath: Path): IDETestContext {
  pluginPath.copyToRecursively( paths.pluginsDir, followLinks = false)
  return this
}