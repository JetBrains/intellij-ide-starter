package com.intellij.tools.ide.metrics.collector.starter.collector

import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.metrics.collector.OpenTelemetrySpanCollector
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics

class StarterTelemetrySpanCollector(spanNames: List<String>, aliases: Map<String, String> = mapOf()) :
  OpenTelemetrySpanCollector(spanNames, aliases), StarterMetricsCollector {
  override fun collect(runContext: IDERunContext): List<PerformanceMetrics.Metric> = collect(runContext.logsDir)
}