package com.intellij.ide.starter.telemetry

import com.intellij.ide.starter.di.di
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import org.kodein.di.direct
import org.kodein.di.instance

interface TestTelemetryService {
  companion object {
    val instance: TestTelemetryService by lazy { di.direct.instance() }

    fun spanBuilder(spanName: String): SpanBuilder {
      return instance.getTracer().spanBuilder(spanName)
    }
  }

  fun getTracer(): Tracer

  fun shutdown()
}

inline fun <T> computeWithSpan(spanName: String, operation: (Span) -> T): T {
  return TestTelemetryService.spanBuilder(spanName).use(operation)
}

inline fun <T> SpanBuilder.use(operation: (Span) -> T): T {
  return startSpan().useWithoutActiveScope { span ->
    span.makeCurrent().use {
      operation(span)
    }
  }
}

/**
 * Does not activate the span scope, so **new spans created inside will not be linked to [this] span**.
 * Consider using [use] to also activate the scope.
 */
inline fun <T> Span.useWithoutActiveScope(operation: (Span) -> T): T {
  try {
    return operation(this)
  }
  catch (e: Throwable) {
    recordException(e)
    setStatus(StatusCode.ERROR)
    throw e
  }
  finally {
    end()
  }
}