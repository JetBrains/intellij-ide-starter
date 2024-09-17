## 2024.3

### Improvements

* Parsing of 2Gb opentelemetry.json now requires 3x less heap size
* Reworked and unified metrics collection. There is `MetricsCollector` interface with the main implementations
  `StarterTelemetrySpanCollector` and
  `StarterTelemetryJsonMeterCollector` for collection spans and meters respectively.

### Breaking changes

* Method `com.intellij.tools.ide.metrics.collector.starter.collector.getMetricsFromSpanAndChildren` was removed.
  Please use `StarterTelemetrySpanCollector(spanFilter).collect(ideStartResult.runContext)` instead.
* In `com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics.Metric` field `value` is now `Int` instead of `Long`.

## 2024.2

### Improvements

* A new protocol to communicate with an IDE is introduced — Driver. See [doc](../../intellij.tools.ide.starter.driver/README.md) for more
  details.