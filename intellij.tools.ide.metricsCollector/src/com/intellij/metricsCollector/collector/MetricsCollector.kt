package com.intellij.metricsCollector.collector

import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.runner.IDERunContext

interface MetricsCollector {
  /**
   * Collect IDE metrics.
   * [runContext] [IDERunContext] (and not [IDEStartResult]) is needed since collection can happen multiple times during the test.
   *
   * For example:
   * - First collection
   * - Test does something
   * - Second collection
   * - Calculation of diff between first and second collection via [com.intellij.metricsCollector.MetricsDiffCalculator]
   */
  fun collect(runContext: IDERunContext): List<PerformanceMetrics.Metric>
}
