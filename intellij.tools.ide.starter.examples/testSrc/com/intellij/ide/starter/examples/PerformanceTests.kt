package com.intellij.ide.starter.examples

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.utils.Git
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics.Metric
import com.intellij.tools.ide.metrics.collector.starter.fus.StatisticsEventsHarvester
import com.intellij.tools.ide.metrics.collector.starter.fus.filterByEventId
import com.intellij.tools.ide.metrics.collector.starter.fus.getDataFromEvent
import com.intellij.tools.ide.metrics.collector.starter.metrics.extractIndexingMetrics
import com.intellij.tools.ide.metrics.collector.telemetry.SpanFilter
import com.intellij.tools.ide.metrics.collector.telemetry.getMetricsFromSpanAndChildren
import com.intellij.tools.ide.performanceTesting.commands.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.nio.file.Path
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
    val indexingMetrics = extractIndexingMetrics(results).getListOfIndexingMetrics()
    writeMetricsToCSV(results, indexingMetrics)
  }


  @Test
  fun openFile() {
    val commandsOpenFile = CommandChain()
      .startProfile("openFile")
      .openFile("src/main/java/com/quantum/pages/GooglePage.java")
      .stopProfile().exitApp()
    val result = context.runIDE(commands = commandsOpenFile, launchName = "openFile")

    val metrics = getMetricsFromSpanAndChildren(
      (result.runContext.logsDir / "opentelemetry.json"), SpanFilter.nameEquals("firstCodeAnalysis")
    )
    writeMetricsToCSV(result, metrics)
  }


  @Test
  fun searchEverywhere() {
    val commandsSearch = CommandChain()
      .startProfile("searchEverywhere")
      .searchEverywhere("symbol", "", "GooglePage", false, true)
      .stopProfile().exitApp()
    val result = context.runIDE(commands = commandsSearch, launchName = "searchEverywhere")
    val metrics = getMetricsFromSpanAndChildren(
      (result.runContext.logsDir / "opentelemetry.json"),
      SpanFilter.nameEquals("searchEverywhere")
    )

    writeMetricsToCSV(result, metrics)
  }


  @Test
  fun reloadMavenProject() {
    val reloadMavenProject = CommandChain()
      .startProfile("reloadMavenProject")
      .checkoutBranch("43b46f36cef25076a8014e247c4fdf4499d924da", "temp")
      .waitForSmartMode()
      .importMavenProject()
      .stopProfile()
      .exitApp()
    val result = context.runIDE(commands = reloadMavenProject, launchName = "reloadMavenProject")
    val firstImport = StatisticsEventsHarvester(context).getStatisticEventsByGroup("project.import")
      .filterByEventId("import_project.finished").sortedBy {
        it.time
      }.first().getDataFromEvent<Long>(EventFields.DurationMs.name)
    val lastImport = StatisticsEventsHarvester(context).getStatisticEventsByGroup("project.import")
      .filterByEventId("import_project.finished").sortedBy {
        it.time
      }.last().getDataFromEvent<Long>(EventFields.DurationMs.name)
    Git.checkout(context.resolvedProjectHome, "master")
    writeMetricsToCSV(result, listOf(Metric.newDuration("maven.import/lastImport", lastImport)))
    writeMetricsToCSV(result, listOf(Metric.newDuration("maven.import/firstImport", firstImport)))
  }

  @Test
  fun runRunConfiguration() {
    val configurationName = "SimpleRun"
    val startRunConfiguration = CommandChain()
      .startProfile("runConfiguration")
      .runConfiguration(configurationName)
      .stopProfile()
      .exitApp()
    val result = context.runIDE(commands = startRunConfiguration, launchName = "runConfiguration")
    var metric = -1L
    result.runContext.logsDir.forEachDirectoryEntry {
      if(it.isRegularFile()){
        it.forEachLine {
          if(it.contains("processTerminated in: $configurationName:")) {
            metric = it.split(":").last().trim().toLong()
          }
        }
      }
    }
    writeMetricsToCSV(result, listOf(Metric.newDuration("runConfiguration", metric)))
  }

  @Test
  fun runBuild() {
    val startRunConfiguration = CommandChain()
      .startProfile("build")
      .build()
      .stopProfile()
      .exitApp()
    val result = context.runIDE(commands = startRunConfiguration, launchName = "build")
    val metrics = getMetricsFromSpanAndChildren(
      (result.runContext.logsDir / "opentelemetry.json"),
      SpanFilter.nameEquals("build_compilation_duration")
    )
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
    val metrics = getMetricsFromSpanAndChildren(
      result.runContext.logsDir / "opentelemetry.json",
      SpanFilter.nameEquals("findUsages")
    )
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
    val metrics = getMetricsFromSpanAndChildren(
      result.runContext.logsDir / "opentelemetry.json",
      SpanFilter.nameEquals("typing")
    )
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
    val metrics = getMetricsFromSpanAndChildren(
      result.runContext.logsDir / "opentelemetry.json",
      SpanFilter.nameEquals("completion")
    )
    writeMetricsToCSV(result, metrics)
  }
}