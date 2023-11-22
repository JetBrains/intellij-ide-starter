package com.intellij.tools.ide.metrics.collector.starter.collector

import com.intellij.ide.starter.ide.IDETestContext.Companion.OPENTELEMETRY_FILE
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.telemetry.SpanFilter
import com.intellij.tools.ide.metrics.collector.telemetry.getMetricsFromSpanAndChildren

fun getMetricsFromSpanAndChildren(startResult: IDEStartResult, filter: SpanFilter): List<PerformanceMetrics.Metric> {
  val opentelemetryFile = startResult.runContext.logsDir.resolve(OPENTELEMETRY_FILE)
  return getMetricsFromSpanAndChildren(opentelemetryFile, filter)
}
