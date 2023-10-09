package com.intellij.tools.ide.metrics.collector.starter.collector

import com.intellij.ide.starter.ide.IDETestContext.Companion.OPENTELEMETRY_FILE
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.tools.ide.metrics.collector.collector.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.metrics.getMetricsFromSpanAndChildren
import com.intellij.tools.ide.metrics.collector.telemetry.SpanFilter

fun getMetricsFromSpanAndChildren(startResult: IDEStartResult, filter: SpanFilter): List<PerformanceMetrics.Metric> {
  val opentelemetryFile = startResult.runContext.logsDir.resolve(OPENTELEMETRY_FILE).toFile()
  return getMetricsFromSpanAndChildren(opentelemetryFile, filter)
}
