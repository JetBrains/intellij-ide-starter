package com.intellij.tools.ide.metrics.collector.starter.publishing

import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.metrics.collector.collector.CompareSetting
import com.intellij.tools.ide.metrics.collector.collector.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.starter.collector.MetricsCollector
import com.intellij.tools.ide.util.common.PrintFailuresMode
import com.intellij.tools.ide.util.common.withRetry

/**
 * Aggregate metrics from different collectors [MetricsCollector]
 * Eg: OpenTelemetry spans (.json), OpenTelemetry meters (.csv), or any other custom collectors.
 * Publish metrics with custom publishing logic.
 * Can compare metrics if needed during publishing.
 *
 * Note:
 * Unfortunately, it cannot be included in DI for now as a one of the default report publishers.
 * Since there is no easy way to say, that current test launch should be considered as a perf test launch and we should also publish its results.
 */
abstract class MetricsPublisher<T> {
  protected val metricsCollectors: MutableList<MetricsCollector> = mutableListOf()
  protected abstract var publishAction: (IDEStartResult, List<PerformanceMetrics.Metric>) -> Unit
  protected var metricsComparisonConfiguration: Map<String, CompareSetting> = mutableMapOf()

  protected fun asTypeT(): T = this as T

  fun addMetricsCollector(collector: MetricsCollector): T {
    metricsCollectors.add(collector)
    return this.asTypeT()
  }

  fun configurePublishAction(publishAction: (IDEStartResult, List<PerformanceMetrics.Metric>) -> Unit): T {
    this.publishAction = publishAction
    return this.asTypeT()
  }

  fun setCompareSettingForMetrics(metricsNames: List<String>, compareSetting: CompareSetting): T {
    this.metricsComparisonConfiguration = metricsNames.associateWith { compareSetting }
    return this.asTypeT()
  }

  fun getCollectedMetrics(runContext: IDERunContext): List<PerformanceMetrics.Metric> = metricsCollectors.flatMap {
    withRetry(messageOnFailure = "Failure on metrics collection", printFailuresMode = PrintFailuresMode.LAST) { it.collect(runContext) }
    ?: throw RuntimeException("Couldn't collect metrics from collector ${it::class.simpleName}")
  }

  fun getCollectedMetrics(ideStartResult: IDEStartResult): List<PerformanceMetrics.Metric> = getCollectedMetrics(ideStartResult.runContext)
}