package com.intellij.tools.ide.metrics.collector.starter.collector

import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.metrics.collector.OpenTelemetryJsonMeterCollector
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import io.opentelemetry.sdk.metrics.data.LongPointData
import io.opentelemetry.sdk.metrics.data.MetricData
import java.nio.file.Path

class StarterTelemetryJsonMeterCollector(
  metricsSelectionStrategy: MetricsSelectionStrategy,
  meterFilter: (MetricData) -> Boolean
) : OpenTelemetryJsonMeterCollector(metricsSelectionStrategy, meterFilter), StarterMetricsCollector {
  override fun collect(runContext: IDERunContext): List<PerformanceMetrics.Metric> = collect(runContext.logsDir)
  fun collect(runContext: IDERunContext, transform: (Map.Entry<String, LongPointData>) -> Pair<String, Int>): List<PerformanceMetrics.Metric> = collect(runContext.logsDir, transform)
}