package com.intellij.metricsCollector.analysis

import com.intellij.metricsCollector.collector.PerformanceMetrics

enum class Conclusion {
  UNKNOWN,
  FASTER,
  PROBABLY_FASTER,
  SAME,
  PROBABLY_SLOWER,
  SLOWER
}

interface FailureAnalysis {
  fun analyseResults(sortedPreviousMetrics: MutableList<PerformanceMetrics>,
                     metricName: String,
                     currentResult: Number,
                     testName: String,
                     notifierHook: (Conclusion) -> Unit)
}

class NoFailureAnalysis: FailureAnalysis{
  override fun analyseResults(sortedPreviousMetrics: MutableList<PerformanceMetrics>,
                              metricName: String,
                              currentResult: Number,
                              testName: String,
                              notifierHook: (Conclusion) -> Unit) {}
}