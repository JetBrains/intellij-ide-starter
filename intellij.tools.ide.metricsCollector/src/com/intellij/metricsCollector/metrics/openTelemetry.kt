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
  val metrics = spansNames.map { spanName -> getMetrics(context, spanName) }.flatten()
  return PerformanceMetricsDto.create(
    projectName = context.testName,
    buildNumber = BuildNumber.fromStringWithProductCode(context.ide.build, context.ide.productCode)!!,
    metrics = metrics
  )
}

fun getSingleMetric(file: File, nameOfSpan: String): Metric<*> {
  return getMetrics(file, DEFAULT_SPAN_NAME).first { it.id.name == nameOfSpan }
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
fun getMetrics(file: File, nameOfSpan: String): MutableCollection<Metric<*>> {
  return getAllSpansCombined(file, filterFunction = { spanName -> spanName == nameOfSpan })
}

fun getAllSpans(file: File,
                filterFunction: (spanName: String) -> Boolean = { true }): MutableMap<String, MutableList<MetricWithAttributes>> {
  val (spanToMetricMap, allSpans) = getSpans(file)
  for (span in allSpans) {
    val operationName = span.get("operationName").textValue()
    if (filterFunction(operationName)) {
      val metric = MetricWithAttributes(Metric(Duration(operationName), getDuration(span)))
      populateAttributes(metric, span)
      spanToMetricMap.getOrPut(operationName) { mutableListOf() }.add(metric)
      processChildren(spanToMetricMap, allSpans, span.get("spanID").textValue())
    }
  }
  return spanToMetricMap
}

fun getAllSpansCombined(file: File, filterFunction: (spanName: String) -> Boolean = { true }): MutableCollection<Metric<*>>{
  return combineMetrics(getAllSpans(file, filterFunction))
}

/**
 * The method reports duration of `nameSpan` and in case of all publishOnlyParent=false its children spans.
 * Besides, all attributes are reported as counters.
 */
fun getMetrics(context: IDETestContext, nameOfSpan: String, publishOnlyParent: Boolean = false): MutableCollection<Metric<*>> {
  val opentelemetryFile = context.paths.logsDir.resolve(OPENTELEMETRY_FILE).toFile()
  if (publishOnlyParent) {
    return mutableListOf(getSingleMetric(opentelemetryFile, nameOfSpan))
  }
  return getMetrics(opentelemetryFile, nameOfSpan)
}

private fun getSpans(file: File): Pair<MutableMap<String, MutableList<MetricWithAttributes>>, JsonNode> {
  val root = jacksonObjectMapper().readTree(file)
  val spanToMetricMap = mutableMapOf<String, MutableList<MetricWithAttributes>>()
  val allSpans = root.get("data")[0].get("spans")
  if (allSpans.isEmpty) println("No spans have been found")
  return Pair(spanToMetricMap, allSpans)
}

private fun combineMetrics(metrics: MutableMap<String, MutableList<MetricWithAttributes>>): MutableCollection<Metric<*>> {
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
      val sum = entry.value.sumOf { it.metric.value.toLong() }
      val mean = sum / entry.value.size
      val variance = (entry.value
        .map { (it.metric.value.toDouble() - mean.toDouble()).pow(2) }
        .reduce { acc, d -> acc + d }) / (entry.value.size)
      for (attr in mediumAttributes) {
        if (attr.key.endsWith("#max")) {
          result.add(Metric(Duration(attr.key), attr.value.max()))
          continue
        }
        if (attr.key.endsWith("#p90")) {
          continue
        }
        if(attr.key.endsWith("#mean_value")){
          result.add(Metric(Duration(attr.key), attr.value.average().toLong()))
          continue
        }

        result.add(Metric(Duration(attr.key + "#mean_value"), attr.value.average().toLong()))
        result.add(Metric(Duration(attr.key + "#standard_deviation"), standardDeviation(attr.value)))
      }
      result.add(Metric(Duration(entry.key), sum))
      result.add(Metric(Duration(entry.key + "#mean_value"), mean))
      result.add(Metric(Duration(entry.key + "#standard_deviation"), sqrt(variance).toLong()))
    }
  }
  return result
}

private fun standardDeviation(data: Collection<Long>): Long {
  val mean = data.average()
  val variance = (data
    .map { (it - mean).pow(2) }
    .reduce { acc, d -> acc + d }) / (data.size)
  return sqrt(variance).toLong()
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

data class FilePathFieMetric(val value: Long, val path: String, val timeout: Boolean)

fun getValues(file: File, nameOfSpan: String): List<FilePathFieMetric> {
  val res = mutableListOf<FilePathFieMetric>()
  val root = jacksonObjectMapper().readTree(file)
  val allSpans = root.get("data")[0].get("spans")
  if (allSpans.isEmpty) println("No spans have been found")
  for (span in allSpans) {
    if (span.get("operationName").textValue() == nameOfSpan) {
      val duration = getDuration(span)
      val path = span.get("tags").firstOrNull { it.get("key").textValue() == "filePath" }?.get("value")?.textValue()
      val timeout = span.get("tags").firstOrNull { it.get("key").textValue() == "timeout" }?.get("value")?.textValue()
      if (path != null) {
        val time = timeout != null
        res.add(FilePathFieMetric(duration, path, time))
      }
    }
  }
  return res
}
