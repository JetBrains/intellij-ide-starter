package com.intellij.tools.ide.metrics.collector.starter.publishing

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.starter.collector.StarterMetricsCollector
import com.intellij.tools.ide.metrics.collector.starter.collector.StarterTelemetryJsonMeterCollector
import com.intellij.tools.ide.metrics.collector.starter.collector.StarterTelemetrySpanCollector
import com.intellij.tools.ide.util.common.PrintFailuresMode
import com.intellij.tools.ide.util.common.withRetryBlocking
import io.opentelemetry.sdk.metrics.data.MetricData
import org.kodein.di.direct
import org.kodein.di.provider

/**
 * Aggregate metrics from different collectors [StarterMetricsCollector]
 * Eg: OpenTelemetry spans (.json), OpenTelemetry meters (.csv), or any other custom collectors.
 * Publish metrics with custom publishing logic.
 * Can compare metrics if needed during publishing.
 */
abstract class MetricsPublisher<T> {
  protected val metricsCollectors: MutableList<StarterMetricsCollector> = mutableListOf()

  protected abstract var publishAction: (IDEStartResult, List<PerformanceMetrics.Metric>) -> Unit

  fun addMetricsCollector(vararg collectors: StarterMetricsCollector): MetricsPublisher<T> {
    metricsCollectors.addAll(collectors)
    return this
  }

  fun configurePublishAction(publishAction: (IDEStartResult, List<PerformanceMetrics.Metric>) -> Unit): MetricsPublisher<T> {
    this.publishAction = publishAction
    return this
  }

  fun getCollectedMetrics(runContext: IDERunContext): List<PerformanceMetrics.Metric> = metricsCollectors.flatMap {
    withRetryBlocking(
      messageOnFailure = "Failure on metrics collection",
      printFailuresMode = PrintFailuresMode.ONLY_LAST_FAILURE,
    ) { it.collect(runContext) }
    ?: throw RuntimeException("Couldn't collect metrics from collector ${it::class.simpleName}")
  }

  fun getCollectedMetrics(ideStartResult: IDEStartResult): List<PerformanceMetrics.Metric> = getCollectedMetrics(ideStartResult.runContext)

  /** Collect metrics from all registered collectors and publish them */
  abstract fun publish(ideStartResult: IDEStartResult): MetricsPublisher<T>
}

/** Return a new instance of metric publisher */
val IDEStartResult.newMetricsPublisher: MetricsPublisher<*>
  get() {
    try {
      return di.direct.provider<MetricsPublisher<*>>().invoke()
    }
    catch (e: Throwable) {
      throw IllegalStateException("No metrics publishers were registered in Starter DI")
    }
  }

/**
 * Shortcut for creating [StarterTelemetrySpanCollector], adding it as a collector, and publishing the collected metrics.
 * @see [com.intellij.tools.ide.metrics.collector.starter.publishing.MetricsPublisher.publish]
 */
fun MetricsPublisher<*>.publishOnlySpans(
  ideStartResult: IDEStartResult,
  spanNames: List<String>,
  aliases: Map<String, String> = mapOf(),
) {
  this.addMetricsCollector(StarterTelemetrySpanCollector(spanNames = spanNames, aliases = aliases))
    .publish(ideStartResult)
}

/**
 * Shortcut for creating [StarterTelemetryJsonMeterCollector], adding it as a collector, and publishing the collected metrics
 * @see [com.intellij.tools.ide.metrics.collector.starter.publishing.MetricsPublisher.publish]
 */
fun MetricsPublisher<*>.publishOnlyMeters(
  ideStartResult: IDEStartResult,
  metricsSelectionStrategy: MetricsSelectionStrategy,
  meterFilter: (MetricData) -> Boolean,
) {
  this.addMetricsCollector(StarterTelemetryJsonMeterCollector(metricsSelectionStrategy, meterFilter))
    .publish(ideStartResult)
}