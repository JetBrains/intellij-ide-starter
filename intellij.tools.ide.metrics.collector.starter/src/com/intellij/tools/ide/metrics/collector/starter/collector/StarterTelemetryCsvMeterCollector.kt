package com.intellij.tools.ide.metrics.collector.starter.collector

import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.metrics.collector.OpenTelemetryCsvMeterCollector
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import io.opentelemetry.sdk.metrics.data.PointData
import org.jetbrains.annotations.ApiStatus

@ApiStatus.ScheduledForRemoval
@Deprecated("Use com.intellij.tools.ide.metrics.collector.starter.collector.StarterTelemetryJsonMeterCollector")
class StarterTelemetryCsvMeterCollector(metricsSelectionStrategy: MetricsSelectionStrategy,
                                        metersFilter: (Map.Entry<String, List<PointData>>) -> Boolean) :
  OpenTelemetryCsvMeterCollector(metricsSelectionStrategy, metersFilter), StarterMetricsCollector {
  override fun collect(runContext: IDERunContext): List<PerformanceMetrics.Metric> = collect(runContext.logsDir)
}