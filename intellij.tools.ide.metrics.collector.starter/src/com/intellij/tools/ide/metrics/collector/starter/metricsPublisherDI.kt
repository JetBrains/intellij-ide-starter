package com.intellij.tools.ide.metrics.collector.starter

import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.starter.publishing.MetricsPublisher
import com.intellij.tools.ide.util.common.logError
import org.kodein.di.DI
import org.kodein.di.bindProvider

val metricsPublisherDI by DI.Module {
  bindProvider<MetricsPublisher<*>>() {
    object : MetricsPublisher<Any>() {
      override var publishAction: (IDEStartResult, List<PerformanceMetrics.Metric>) -> Unit = { _, _ -> }

      override fun publish(ideStartResult: IDEStartResult): MetricsPublisher<Any> {
        logError("Default metrics publisher is registered. If you need to publish your metrics - register your own publisher via DI.")
        return this
      }
    }
  }
}