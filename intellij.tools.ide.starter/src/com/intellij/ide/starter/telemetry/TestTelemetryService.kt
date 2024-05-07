package com.intellij.ide.starter.telemetry

import com.intellij.ide.starter.di.di
import io.opentelemetry.api.trace.Tracer
import org.kodein.di.direct
import org.kodein.di.instance

interface TestTelemetryService {
  companion object {
    val instance: TestTelemetryService by lazy { di.direct.instance() }
  }

  fun getTracer(): Tracer

  fun shutdown()
}