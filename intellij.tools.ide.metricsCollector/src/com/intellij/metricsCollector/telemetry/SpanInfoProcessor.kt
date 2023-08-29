package com.intellij.metricsCollector.telemetry

class SpanInfoProcessor : SpanProcessor<SpanElement> {
  override fun process(span: SpanElement): SpanElement? {
    if (!span.isWarmup) {
      return span
    }
    return null
  }
}