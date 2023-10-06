package com.intellij.metrics.collector.starter.collector

import com.intellij.ide.starter.ide.IDETestContext.Companion.OPENTELEMETRY_FILE
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.metricsCollector.collector.PerformanceMetrics
import com.intellij.metricsCollector.metrics.getMetricsFromSpanAndChildren
import com.intellij.metricsCollector.telemetry.SpanFilter

fun getMetricsFromSpanAndChildren(startResult: IDEStartResult, filter: SpanFilter): List<PerformanceMetrics.Metric> {
  val opentelemetryFile = startResult.runContext.logsDir.resolve(OPENTELEMETRY_FILE).toFile()
  return getMetricsFromSpanAndChildren(opentelemetryFile, filter)
}
