package com.intellij.tools.ide.metrics.collector.starter.collector

import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.metrics.collector.OpenTelemetrySpanCollector
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.telemetry.SpanFilter

class StarterTelemetrySpanCollector(
  spanFilter: SpanFilter,
  spanAliases: Map<String, String> = mapOf(),
) : OpenTelemetrySpanCollector(spanFilter, spanAliases), StarterMetricsCollector {
  override fun collect(runContext: IDERunContext): List<PerformanceMetrics.Metric> = collect(runContext.logsDir)
}