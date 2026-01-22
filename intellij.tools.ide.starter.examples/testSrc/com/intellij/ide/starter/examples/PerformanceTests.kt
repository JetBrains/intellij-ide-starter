package com.intellij.ide.starter.examples

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics.Metric
import com.intellij.tools.ide.metrics.collector.starter.fus.StatisticsEventsHarvester
import com.intellij.tools.ide.metrics.collector.starter.fus.filterByEventId
import com.intellij.tools.ide.metrics.collector.starter.fus.getDataFromEvent
import com.intellij.tools.ide.metrics.collector.starter.metrics.extractIndexingMetrics
import com.intellij.tools.ide.metrics.collector.telemetry.SpanFilter
import com.intellij.tools.ide.performanceTesting.commands.*
import com.intellij.util.indexing.diagnostic.dto.IndexingMetric
import com.intellij.util.indexing.diagnostic.dto.getListOfIndexingMetrics
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.io.path.*

@Disabled("Requires local installation of IDE, configs and project")
class PerformanceTests {

  companion object {
    private lateinit var context: IDETestContext

    @BeforeAll
    @JvmStatic
    fun initContext() {
      context = Setup.setupTestContext()
    }
  }

  @Test
  fun openProjectIndexing() {
    val commands = CommandChain().startProfile("indexing").waitForSmartMode().stopProfile().exitApp()
    val contextForIndexing = context.copy().executeDuringIndexing()
    val results = contextForIndexing.runIDE(commands = commands, launchName = "indexing")
    val indexingMetrics = extractIndexingMetrics(results).getListOfIndexingMetrics().map {
      when (it) {
        is IndexingMetric.Duration -> PerformanceMetrics.newDuration(it.name, it.durationMillis)
        is IndexingMetric.Counter -> PerformanceMetrics.newCounter(it.name, it.value)
      }
    }
    writeMetricsToCSV(results, indexingMetrics)
  }


  @Test
  fun openFile() {
    val commandsOpenFile = CommandChain()
      .startProfile("openFile")
      .openFile("src/main/java/com/quantum/pages/GooglePage.java")
      .stopProfile().exitApp()
    val result = context.runIDE(commands = commandsOpenFile, launchName = "openFile")

    val metrics = getMetricsFromSpanAndChildren(result, SpanFilter.nameEquals("firstCodeAnalysis"))
    writeMetricsToCSV(result, metrics)
  }


  @Test
  fun searchEverywhere() {
    val commandsSearch = CommandChain()
      .startProfile("searchEverywhere")
      .searchEverywhere("symbol", "", "GooglePage", false, true)
      .stopProfile().exitApp()
    val result = context.runIDE(commands = commandsSearch, launchName = "searchEverywhere")
    val metrics = getMetricsFromSpanAndChildren(result, SpanFilter.nameEquals("searchEverywhere"))
    writeMetricsToCSV(result, metrics)
  }


  @Test
  fun reloadMavenProject() {
    val reloadMavenProject = CommandChain()
      .startProfile("reloadMavenProject")
      .importMavenProject()
      .checkoutBranch("43b46f36cef25076a8014e247c4fdf4499d924da", "temp")
      .waitForSmartMode()
      .importMavenProject()
      .stopProfile()
      .exitApp()
    val result = context.runIDE(commands = reloadMavenProject, launchName = "reloadMavenProject") {
      addVMOptionsPatch {
        skipIndicesInitialization(true)
        disableAutoImport(true)
      }
    }
    val totalImport = StatisticsEventsHarvester(context).getStatisticEventsByGroup("project.import")
      .filterByEventId("import_project.finished").sortedBy {
        it.time
      }.sumOf { it.getDataFromEvent<Long>(EventFields.DurationMs.name) }.toInt()
    val testTime = getMetricsFromSpanAndChildren(result, SpanFilter.nameEquals("performance_test"))
    writeMetricsToCSV(result, listOf(Metric.newDuration("totalImport", totalImport)) + testTime)
  }

  /**
   * Test works since 2024.1
   */
  @Test
  fun runRunConfiguration() {
    val configurationName = "SimpleRun"
    val startRunConfiguration = CommandChain()
      .startProfile("runConfiguration")
      .runConfiguration(configurationName)
      .stopProfile()
      .exitApp()
    val result = context.runIDE(commands = startRunConfiguration, launchName = "runConfiguration")
    // 2024.2+
    val metricsFromOt = getMetricsFromSpanAndChildren(result, SpanFilter.nameEquals("runRunConfiguration"))
    val metrics = metricsFromOt.ifEmpty {
      //backward compatibility with 2024.1
      result.runContext.logsDir.forEachDirectoryEntry {
        if (it.isRegularFile()) {
          it.forEachLine {
            if (it.contains("processTerminated in:")) {
              return@ifEmpty listOf(Metric.newDuration("runConfiguration", it.split(":").last().trim().toInt()))
            }
          }
        }
      }
      return@ifEmpty emptyList<Metric>()
    }
    writeMetricsToCSV(result, metrics)
  }

  @Test
  fun runBuild() {
    val startRunConfiguration = CommandChain()
      .startProfile("build")
      .build()
      .stopProfile()
      .exitApp()
    val result = context.runIDE(commands = startRunConfiguration, launchName = "build")
    val metrics = getMetricsFromSpanAndChildren(result, SpanFilter.nameEquals("build_compilation_duration"))
    writeMetricsToCSV(result, metrics)
  }


  @Test
  fun findUsage() {
    val findUsageTest = CommandChain()
      .openFile("src/main/java/com/quantum/pages/GooglePage.java")
      .goto(43, 18)
      .startProfile("findUsage")
      .findUsages("").stopProfile().exitApp()
    val result = context.runIDE(commands = findUsageTest, launchName = "findUsages")
    val metrics = getMetricsFromSpanAndChildren(result, SpanFilter.nameEquals("findUsages"))
    writeMetricsToCSV(result, metrics)
  }

  @Test
  fun typing() {
    val typingTest = CommandChain()
      .openFile("src/main/java/com/quantum/pages/GooglePage.java")
      .goto(32, 1)
      .startProfile("typing")
      .delayType(150, "public void fooBar(String searchKey){}")
      .stopProfile().exitApp()
    val result = context.runIDE(commands = typingTest, launchName = "typing")
    val metrics = getMetricsFromSpanAndChildren(result, SpanFilter.nameEquals("typing"))
    writeMetricsToCSV(result, metrics)
  }

  @Test
  fun completion() {
    val completion = CommandChain()
      .openFile("src/main/java/com/quantum/pages/GooglePage.java")
      .goto(34, 17)
      .startProfile("completion")
      .doComplete(1)
      .stopProfile()
      .exitApp()
    val result = context.runIDE(commands = completion, launchName = "completion")
    val metrics = getMetricsFromSpanAndChildren(result, SpanFilter.nameEquals("completion"))
    writeMetricsToCSV(result, metrics)
  }
}