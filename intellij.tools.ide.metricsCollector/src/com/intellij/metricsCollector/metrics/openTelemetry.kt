package com.intellij.metricsCollector.metrics

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IDETestContext.Companion.OPENTELEMETRY_FILE
import com.intellij.metricsCollector.collector.PerformanceMetrics.Metric
import com.intellij.metricsCollector.collector.PerformanceMetrics.MetricId.Counter
import com.intellij.metricsCollector.collector.PerformanceMetrics.MetricId.Duration
import com.intellij.metricsCollector.collector.PerformanceMetricsDto
import com.intellij.openapi.util.BuildNumber
import java.io.File
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

const val TOTAL_TEST_TIMER_NAME: String = "test"
const val DEFAULT_SPAN_NAME: String = "performance_test"

data class MetricWithAttributes(val metric: Metric<*>,
                                val attributes: MutableList<Metric<*>> = mutableListOf())

@Suppress("unused")
fun getOpenTelemetry(context: IDETestContext): PerformanceMetricsDto {
  return getOpenTelemetry(context, DEFAULT_SPAN_NAME)
}

fun getOpenTelemetry(context: IDETestContext, vararg spansNames: String): PerformanceMetricsDto {
  val metrics = spansNames.map { spanName -> getMetricsFromSpanAndChildren(context, SpanFilter.equals(spanName)) }.flatten()
  return PerformanceMetricsDto.create(
    projectName = context.testName,
    buildNumber = BuildNumber.fromStringWithProductCode(context.ide.build, context.ide.productCode)!!,
    metrics = metrics
  )
}

class SpanFilter(val filter: (String) -> Boolean) {
  companion object {
    fun equals(name: String) = SpanFilter { it == name }
    fun containsIn(names: List<String>) = SpanFilter { it in names }
    fun contain(substring: String) = SpanFilter { it.contains(substring) }
  }
}

/**
 * Reports duration of `nameSpan` and all its children spans.
 * Besides, all attributes are reported as counters.
 * If there are multiple values with the same name:
 * 1. They will be re-numbered `<value>_1`, `<value>_2`, etc and the sum will be recorded as `<value>`.
 * 2. In the sum value, mean value and standard deviation of attribute value will be recorded
 * 2a. If attribute ends with `#max`, in sum the max of max will be recorded
 * 3a. If attribute ends with `#mean_value`, the mean value of mean values will be recorded
 */

fun getMetricsFromSpanAndChildren(file: File, filter: SpanFilter): List<Metric<*>>{
  return combineMetrics(getSpansMetricsMap(file, filter))
}
fun getMetricsFromSpanAndChildren(context: IDETestContext, filter: SpanFilter): List<Metric<*>> {
  val opentelemetryFile = context.paths.logsDir.resolve(OPENTELEMETRY_FILE).toFile()
  return getMetricsFromSpanAndChildren(opentelemetryFile, filter)
}

fun getSpansMetricsMap(file: File,
                       spanFilter: SpanFilter = SpanFilter { true }): MutableMap<String, MutableList<MetricWithAttributes>> {
  val allSpans = getSpans(file)
  val spanToMetricMap = mutableMapOf<String, MutableList<MetricWithAttributes>>()
  for (span in allSpans) {
    val operationName = span.get("operationName").textValue()
    if (spanFilter.filter(operationName)) {
      val metric = MetricWithAttributes(Metric(Duration(operationName), getDuration(span)))
      populateAttributes(metric, span)
      spanToMetricMap.getOrPut(operationName) { mutableListOf() }.add(metric)
      processChildren(spanToMetricMap, allSpans, span.get("spanID").textValue())
    }
  }
  return spanToMetricMap
}

fun processSpans(
  file: File,
  spanFilter: SpanFilter,
  processSpan: (duration: Long, attributes: Map<String, String>) -> Unit) {
  val allSpans = getSpans(file)
  for (span in allSpans) {
    if (spanFilter.filter(span.get("operationName").textValue())) {
      val spanDuration = getDuration(span)
      val spanAttributes = span.get("tags").mapNotNull {
        val key = it.get("key")?.textValue()
        val value = it.get("value")?.textValue()
        if (key != null && value != null) key to value else null
      }.toMap()
      processSpan(spanDuration, spanAttributes)
    }
  }
}

private fun getSpans(file: File): JsonNode {
  val root = jacksonObjectMapper().readTree(file)
  val allSpans = root.get("data")[0].get("spans")
  if (allSpans.isEmpty) println("No spans have been found")
  return allSpans
}

private fun combineMetrics(metrics: Map<String, List<MetricWithAttributes>>): List<Metric<*>> {
  val result = mutableListOf<Metric<*>>()
  metrics.forEach { entry ->
    if (entry.value.size == 1) {
      val metric = entry.value.first()
      result.addAll(getAttributes(entry.key, metric))
      if (metric.metric.id.name != TOTAL_TEST_TIMER_NAME) {
        result.add(metric.metric)
      }
    }
    else {
      var counter = 1
      val mediumAttributes: MutableMap<String, MutableList<Long>> = mutableMapOf()
      entry.value.forEach { metric ->
        val value = metric.metric.value.toLong()
        val spanUpdatedName = entry.key + "_$counter"
        result.add(Metric(Duration(spanUpdatedName), value))
        result.addAll(getAttributes(spanUpdatedName, metric))
        getAttributes(entry.key, metric).forEach {
          val key = it.id.name
          val collection = mediumAttributes.getOrDefault(key, mutableListOf())
          collection.add(it.value.toLong())
          mediumAttributes[key] = collection
        }
        counter++
      }
      for (attr in mediumAttributes) {
        if (attr.key.endsWith("#max")) {
          result.add(Metric(Duration(attr.key), attr.value.max()))
          continue
        }
        if (attr.key.endsWith("#p90")) {
          continue
        }
        if (attr.key.endsWith("#mean_value")) {
          result.add(Metric(Duration(attr.key), attr.value.average().toLong()))
          continue
        }

        result.add(Metric(Duration(attr.key + "#mean_value"), attr.value.average().toLong()))
        result.add(Metric(Duration(attr.key + "#standard_deviation"), standardDeviation(attr.value)))
      }
      val sum = entry.value.sumOf { it.metric.value.toLong() }
      val mean = sum / entry.value.size
      val standardDeviation = standardDeviation(entry.value.map { it.metric.value })
      result.add(Metric(Duration(entry.key), sum))
      result.add(Metric(Duration(entry.key + "#mean_value"), mean))
      result.add(Metric(Duration(entry.key + "#standard_deviation"), standardDeviation))
    }
  }
  return result
}

private fun <T : Number> standardDeviation(data: Collection<T>): Long {
  val mean = data.map { it.toDouble() }.average()
  return sqrt(data.map { (it.toDouble() - mean).pow(2) }.average()).toLong()
}

private fun getAttributes(spanName: String, metric: MetricWithAttributes): Collection<Metric<*>> {
  return metric.attributes.map { attributeMetric ->
    Metric(Counter("$spanName#" + attributeMetric.id.name), attributeMetric.value.toInt())
  }
}

private fun processChildren(spanToMetricMap: MutableMap<String, MutableList<MetricWithAttributes>>,
                            allSpans: JsonNode,
                            parentSpanId: String?) {
  allSpans.forEach { span ->
    span.get("references")?.forEach { reference ->
      if (reference.get("refType")?.textValue() == "CHILD_OF") {
        val spanId = reference.get("spanID").textValue()
        if (spanId == parentSpanId) {
          val spanName = span.get("operationName").textValue()
          val value = getDuration(span)
          if (value != 0L || !shouldAvoidIfZero(span)) {
            val metric = MetricWithAttributes(Metric(Duration(spanName), value))
            populateAttributes(metric, span)
            spanToMetricMap.getOrPut(spanName) { mutableListOf() }.add(metric)
            processChildren(spanToMetricMap, allSpans, span.get("spanID").textValue())
          }
        }
      }
    }
  }
}

private fun getDuration(span: JsonNode) = (span.get("duration").longValue() / 1000.0).roundToLong()

private fun shouldAvoidIfZero(span: JsonNode): Boolean {
  span.get("tags")?.forEach { tag ->
    val attributeName = tag.get("key").textValue()
    if (attributeName == "avoid_null_value") {
      tag.get("value").textValue().runCatching { toBoolean() }.onSuccess {
        return it
      }
    }
  }
  return false
}

private fun populateAttributes(metric: MetricWithAttributes, span: JsonNode) {
  span.get("tags")?.forEach { tag ->
    val attributeName = tag.get("key").textValue()
    tag.get("value").textValue().runCatching { toInt() }.onSuccess {
      metric.attributes.add(Metric(Counter(attributeName), it))
    }
  }
}
