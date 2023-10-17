package com.intellij.tools.ide.metrics.collector.starter.collector

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.telemetry.getMetricsFromSpanAndChildren
import com.intellij.tools.ide.metrics.collector.telemetry.SpanFilter

/**
 * Collect spans from opentelemetry.json and convert it to metrics, understandable by IJ Perf dashboard
 */
class OpenTelemetrySpanCollector(val spanNames: List<String>) : MetricsCollector {
  override fun collect(runContext: IDERunContext): List<PerformanceMetrics.Metric> {
    return getMetricsFromSpanAndChildren(runContext.logsDir.resolve(IDETestContext.OPENTELEMETRY_FILE).toFile(),
                                         SpanFilter.containsIn(spanNames))
  }
}