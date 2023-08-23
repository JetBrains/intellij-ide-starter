package com.intellij.metricsCollector

import com.intellij.ide.starter.utils.intersect
import com.intellij.ide.starter.utils.symmetricDiff
import com.intellij.metricsCollector.collector.PerformanceMetrics
import kotlin.math.abs

/**
 * Calculates the difference between two sets of PerformanceMetrics.Metric objects.
 */
object MetricsDiffCalculator {
  /**
   * @return Creates a new list of metrics with name = metric name and value = diff between old and new value (old minus new)
   *
   * [useAbsoluteValue] If true, the absolute value of difference will be used. Eg: |2 - 3| = 1 (not -1)
   */
  fun calculateDiff(first: Iterable<PerformanceMetrics.Metric>,
                    second: Iterable<PerformanceMetrics.Metric>,
                    useAbsoluteValue: Boolean = true): List<PerformanceMetrics.Metric> {
    val firstMap: Map<String, PerformanceMetrics.Metric> = first.associateBy { it.id.name }
    val secondMap: Map<String, PerformanceMetrics.Metric> = second.associateBy { it.id.name }

    val intersectedKeys = firstMap.intersect(secondMap).sorted()

    val diff: MutableList<PerformanceMetrics.Metric> = intersectedKeys.map { metricName ->
      val valueWithSign = secondMap.getValue(metricName).value - firstMap.getValue(metricName).value
      val value = if (useAbsoluteValue) abs(valueWithSign) else valueWithSign

      secondMap.getValue(metricName).copy(value = value)
    }.toMutableList()

    val symmetricNamesDiff: Set<String> = firstMap.symmetricDiff(secondMap)
    symmetricNamesDiff.forEach { metricName ->
      val firstValue = firstMap[metricName]
      if (firstValue != null) {
        diff.add(firstValue)
      }

      val secondValue = secondMap[metricName]
      if (secondValue != null) {
        diff.add(secondValue)
      }
    }

    return diff
  }
}