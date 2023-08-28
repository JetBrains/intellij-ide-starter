package com.intellij.metricsCollector.metrics

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.ide.starter.ide.IDETestContext.Companion.OPENTELEMETRY_FILE
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.metricsCollector.collector.PerformanceMetrics.Metric
import com.intellij.metricsCollector.collector.PerformanceMetrics.MetricId.Counter
import com.intellij.metricsCollector.collector.PerformanceMetrics.MetricId.Duration
import java.io.File
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

const val TOTAL_TEST_TIMER_NAME: String = "test"
const val DEFAULT_SPAN_NAME: String = "performance_test"

data class MetricWithAttributes(val metric: Metric,
                                val attributes: MutableList<Metric> = mutableListOf())


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
 * 1. They will be re-numbered `<value>_1`, `<value>_2`, etc. and the sum will be recorded as `<value>`.
 * 2. In the sum value, mean value and standard deviation of attribute value will be recorded
 * 2a. If attribute ends with `#max`, in sum the max of max will be recorded
 * 3a. If attribute ends with `#mean_value`, the mean value of mean values will be recorded
 */

fun getMetricsFromSpanAndChildren(file: File, filter: SpanFilter): List<Metric> {
  return combineMetrics(getSpansMetricsMap(file, filter))
}

fun getMetricsBasedOnDiffBetweenSpans(name: String, file: File, parentSpanName: String, fromSpan: String, toSpan: String) : List<Metric> {
  return combineMetrics(getDurationBetweenSpans(name, file, parentSpanName, fromSpan, toSpan))
}
fun getMetricsFromSpanAndChildren(startResult: IDEStartResult, filter: SpanFilter): List<Metric> {
  val opentelemetryFile = startResult.runContext.logsDir.resolve(OPENTELEMETRY_FILE).toFile()
  return getMetricsFromSpanAndChildren(opentelemetryFile, filter)
}

fun getSpansMetricsMap(file: File,
                       spanFilter: SpanFilter = SpanFilter { true }): MutableMap<String, MutableList<MetricWithAttributes>> {
  val allSpans = getSpans(file)
  val spanToMetricMap = mutableMapOf<String, MutableList<MetricWithAttributes>>()
  for (span in allSpans) {
    val operationName = span.get("operationName").textValue()
    if (spanFilter.filter(operationName) && !isWarmup(span)) {
      val metric = MetricWithAttributes(Metric(Duration(operationName), getDuration(span)))
      populateAttributes(metric, span)
      spanToMetricMap.getOrPut(operationName) { mutableListOf() }.add(metric)
      processChildren(spanToMetricMap, allSpans, span.get("spanID").textValue())
    }
  }
  return spanToMetricMap
}

/**
 * Calculates the duration between two spans in a given file.
 *
 * @param name The name of the metric.
 * @param file The file containing the spans.
 * @param parentSpanName The name of the parent span.
 * @param fromSpan The start span ID.
 * @param toSpan The end span ID.
 * @return A map containing the metric with attributes.
 */
fun getDurationBetweenSpans(name: String, file: File, parentSpanName: String, fromSpan: String, toSpan: String):  Map<String, List<MetricWithAttributes>> {
  val allSpans = getSpans(file)
  val fromSpans = mutableListOf<SpanInfo>()
  val toSpans = mutableListOf<SpanInfo>()
  for (span in allSpans) {
    val operationName = span.get("operationName").textValue()
    if (operationName == parentSpanName) {
      if (!isWarmup(allSpans)) {
        processChildrenSemantic(fromSpans, toSpans, allSpans, fromSpan, toSpan, span.get("spanID").textValue())
       }
    }
  }
  val sortedFromSpans = fromSpans.sortedByDescending { info -> info.timeStamp }
  val sortedToSpans = toSpans.sortedByDescending { info -> info.timeStamp }
  val metrics = mutableListOf<MetricWithAttributes>()
  assert(toSpans.size >= fromSpans.size) {
    "size of toSpans is ${toSpans.size}, but size of fromSpans is ${fromSpans.size}"
  }
  for(i in fromSpans.size -1 downTo 0 ) {
    val duration = sortedToSpans[i].timeStamp - sortedFromSpans[i].timeStamp + sortedToSpans[i].duration
    val metric = MetricWithAttributes(Metric(Duration(name), duration))
    metrics.add(metric)
  }
  val map = mutableMapOf<String, List<MetricWithAttributes>>()
  map[name] = metrics
  return map
}


fun processChildrenSemantic(fromSpans: MutableList<SpanInfo>, toSpans: MutableList<SpanInfo>,
                            allSpans: JsonNode,
                            fromSpan: String,
                            toSpan: String,
                            parentSpanId: String?) {
  allSpans.forEach { span ->
    span.get("references")?.forEach { reference ->
      if (reference.get("refType")?.textValue() == "CHILD_OF") {
        val spanId = reference.get("spanID").textValue()
        if (spanId == parentSpanId && !isWarmup(span)) {
          val spanName = span.get("operationName").textValue()
          if (spanName == toSpan) {
            val value = getDuration(span)
            val timeStamp = getStartTime(span)
            toSpans.add(SpanInfo(spanName, value, timeStamp))
          }
          if (spanName == fromSpan) {
            val value = getDuration(span)
            val timeStamp = getStartTime(span)
            fromSpans.add(SpanInfo(spanName, value, timeStamp))
          }
          processChildrenSemantic(fromSpans, toSpans, allSpans, fromSpan, toSpan, span.get("spanID").textValue())
        }
      }
    }
  }

}

private fun isWarmup(span: JsonNode) : Boolean {
  val tagNode = span.get("tags")
  if (tagNode == null) {
    return false
  }
  val tags = tagNode.mapNotNull {
    val key = it.get("key")?.textValue()
    val value = it.get("value")?.textValue()
    Pair(key, value)
  }
  return tags.find { it.first == "warmup" && it.second == "true" } != null
}

data class SpanInfo(val name: String, val duration: Long, val timeStamp: Long)

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

private fun combineMetrics(metrics: Map<String, List<MetricWithAttributes>>): List<Metric> {
  val result = mutableListOf<Metric>()
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
        val value = metric.metric.value
        val spanUpdatedName = entry.key + "_$counter"
        result.add(Metric(Duration(spanUpdatedName), value))
        result.addAll(getAttributes(spanUpdatedName, metric))
        getAttributes(entry.key, metric).forEach {
          val key = it.id.name
          val collection = mediumAttributes.getOrDefault(key, mutableListOf())
          collection.add(it.value)
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
      val sum = entry.value.sumOf { it.metric.value }
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

private fun getAttributes(spanName: String, metric: MetricWithAttributes): Collection<Metric> {
  return metric.attributes.map { attributeMetric ->
    Metric(Counter("$spanName#" + attributeMetric.id.name), attributeMetric.value)
  }
}

private fun processChildren(spanToMetricMap: MutableMap<String, MutableList<MetricWithAttributes>>,
                            allSpans: JsonNode,
                            parentSpanId: String?) {
  allSpans.forEach { span ->
    span.get("references")?.forEach { reference ->
      if (reference.get("refType")?.textValue() == "CHILD_OF") {
        val spanId = reference.get("spanID").textValue()
        if (spanId == parentSpanId && !isWarmup(span)) {
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
private fun getStartTime(span: JsonNode) = (span.get("startTime").longValue() / 1000.0).roundToLong()
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
      metric.attributes.add(Metric(Counter(attributeName), it.toLong()))
    }
  }
}
