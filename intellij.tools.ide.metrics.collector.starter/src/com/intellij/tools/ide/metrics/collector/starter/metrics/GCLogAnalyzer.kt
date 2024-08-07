package com.intellij.tools.ide.metrics.collector.starter.metrics

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import java.nio.file.Files
import java.nio.file.Path
import java.text.NumberFormat
import java.util.*
import kotlin.io.path.div
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.minutes

class GCLogAnalyzer(private val ideStartResult: IDEStartResult) {
  companion object {
    private const val gcViewerUrl = "https://packages.jetbrains.team/files/p/ij/intellij-dependencies/gcviewer/gcviewer-1.37-05122022.jar"
  }

  fun getGCMetrics(
    requestedMetrics: Array<String> = arrayOf("gcPause", "fullGCPause", "gcPauseCount", "totalHeapUsedMax", "freedMemoryByGC", "freedMemoryByFullGC", "freedMemory")
  ): Iterable<PerformanceMetrics.Metric> {
    return if ((ideStartResult.runContext.reportsDir / "gcLog.log").toFile().exists()) {
      processGCSummary(findExistingSummary() ?: generateGCSummaryFile(), requestedMetrics)
    }
    else listOf()
  }

  private fun findExistingSummary() = ideStartResult.runContext.reportsDir.toFile().listFiles()?.firstOrNull { file ->
    file.name.startsWith("gcSummary_")
  }?.toPath()

  fun generateGCSummaryFile(): Path {
    val summaryFile = ideStartResult.runContext.reportsDir.resolve("gcSummary_${System.currentTimeMillis()}.log")

    val toolsDir = GlobalPaths.instance.getCacheDirectoryFor("tools")

    val gcViewerPath = toolsDir.resolve(gcViewerUrl.substringAfterLast('/'))
    HttpClient.downloadIfMissing(gcViewerUrl, gcViewerPath)

    runGCViewer(ideStartResult.runContext, gcViewerPath, summaryFile)

    return summaryFile
  }

  fun mergeGCMetrics(statsObject: ObjectNode) {
    val gcMetrics = getGCMetrics()
    val nodeFactory = JsonNodeFactory.instance
    statsObject.putIfAbsent("additionalMetrics", nodeFactory.objectNode())
    val gcObject = nodeFactory.objectNode()
    gcMetrics.forEach {
      gcObject.putIfAbsent(it.id.name, nodeFactory.numberNode(it.value))
    }
    (statsObject.get("additionalMetrics") as ObjectNode).putIfAbsent("gc", gcObject)
  }

  private fun runGCViewer(context: IDERunContext, gcViewer: Path, gcSummary: Path) {
    val gcLogPath = (context.reportsDir / "gcLog.log").toAbsolutePath()
    val logsFiles = Files.list(context.reportsDir).filter { it.fileName != null && it.name.startsWith("gcLog.log") }.toList()
    if (!logsFiles.isEmpty()) {
      val paths = logsFiles.joinToString(separator = ";", transform = { it.pathString })

      // reuse current Java executable, or let's hope 'java' exists somewhere in PATH and it is compatible
      val command = ProcessHandle.current().info().command().orElse(null)
      val javaCommand = if (command.isNullOrBlank()) "java" else command

      try {
        ProcessExecutor(
          "gcviewer",
          workDir = gcViewer.parent, timeout = 1.minutes,
          args = listOf(javaCommand, "-jar", gcViewer.toAbsolutePath().toString(), paths, gcSummary.toAbsolutePath().toString())
        ).start()
      } catch (t: Throwable) {
        println("gcviewer process failed by: ${t.message}")
      }

    }
    else {
      println("$gcLogPath doesn't exists")
    }
  }

  private fun processGCSummary(gcSummary: Path, requestedMetrics: Array<String>): List<PerformanceMetrics.Metric> {
    val gcMetrics = mutableListOf<PerformanceMetrics.Metric>()
    val format = NumberFormat.getNumberInstance(Locale.getDefault())
    if (!gcSummary.toFile().exists()) {
      println("$gcSummary doesn't exists")
      return gcMetrics
    }
    gcSummary.toFile().forEachLine { line ->
      val splitLine = line.split(";")
      if (splitLine.size < 3) {
        return@forEachLine
      }
      val parameter = splitLine[0].trim()
      val value = format.runCatching { parse(splitLine[1].trim()).toDouble() }.getOrNull()
      if (value == null) {
        return@forEachLine
      }

      val type = when (val type = splitLine[2].trim()) {
        "bool" -> return@forEachLine
        "" -> ""
        else -> type
      }
      if (parameter in requestedMetrics) {
        when (type) {
          "-", "M" -> {
            gcMetrics.add(PerformanceMetrics.Metric.newCounter(parameter, value.toInt()))
          }
          "s" -> {
            gcMetrics.add(PerformanceMetrics.Metric.newDuration(parameter, (value * 1000).toInt()))
          }
          else -> {
            println("Unknown type: $type")
          }
        }
      }
    }
    return gcMetrics
  }
}
