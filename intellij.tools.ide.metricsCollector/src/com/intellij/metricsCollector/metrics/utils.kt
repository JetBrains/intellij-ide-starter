package com.intellij.metricsCollector.metrics

import com.intellij.metricsCollector.collector.PerformanceMetrics

fun String.createPerformanceMetricCounter(): PerformanceMetrics.MetricId.Counter = PerformanceMetrics.MetricId.Counter(this)

fun String.createPerformanceMetricDuration(): PerformanceMetrics.MetricId.Duration = PerformanceMetrics.MetricId.Duration(this)