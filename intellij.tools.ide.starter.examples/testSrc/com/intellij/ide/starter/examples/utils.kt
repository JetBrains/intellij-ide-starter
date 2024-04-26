package com.intellij.ide.starter.examples

import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics.Metric
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.bufferedWriter
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