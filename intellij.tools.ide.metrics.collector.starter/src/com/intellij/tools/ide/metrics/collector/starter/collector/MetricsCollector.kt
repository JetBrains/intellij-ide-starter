package com.intellij.tools.ide.metrics.collector.starter.collector

import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics

interface MetricsCollector {
  /**
   * Collect IDE metrics.
   * [runContext] [IDERunContext] (and not [IDEStartResult]) is needed since collection can happen multiple times during the test.
   *
   * For example:
   * - First collection
   * - Test does something
   * - Second collection
   * - Calculation of diff between first and second collection via [com.intellij.tools.ide.metrics.collector.MetricsDiffCalculator]
   */
  fun collect(runContext: IDERunContext): List<PerformanceMetrics.Metric>
}

