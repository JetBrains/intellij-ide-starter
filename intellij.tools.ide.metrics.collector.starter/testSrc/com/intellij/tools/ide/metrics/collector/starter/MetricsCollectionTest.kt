package com.intellij.tools.ide.metrics.collector.starter

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.Starter
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.starter.collector.StarterTelemetryMeterCollector
import com.intellij.tools.ide.metrics.collector.starter.collector.StarterTelemetrySpanCollector
import com.intellij.tools.ide.metrics.collector.starter.metrics.CommonMetrics
import com.intellij.tools.ide.metrics.collector.starter.metrics.GCLogAnalyzer
import com.intellij.tools.ide.metrics.collector.starter.publishing.MetricsPublisher
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import com.intellij.tools.ide.util.common.logOutput
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes


class MetricsPublisherExample : MetricsPublisher<MetricsPublisherExample>() {
  override var publishAction: (IDEStartResult, List<PerformanceMetrics.Metric>) -> Unit = { ideStartResult, metrics ->
    val reportFile: Path = Files.createTempFile("metrics", ".json")

    val metricsSortedByName =
      (metrics
       + CommonMetrics.getJvmMetrics(ideStartResult)
       + CommonMetrics.getAwtMetrics(ideStartResult)
       + GCLogAnalyzer(ideStartResult).getGCMetrics()
       + CommonMetrics.getWriteActionMetrics(ideStartResult)
      ).sortedBy { it.id.name }

    logOutput("All collected metrics: " + metricsSortedByName.joinToString(separator = System.lineSeparator()) {
      "${it.id.name} ${it.value}"
    })

    jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValue(reportFile.toFile(), metricsSortedByName)

    // You can implement your own logic for CIServer and register it via Kodein DI
    // more about that you can find in Starter readme
    ideStartResult.context.publishArtifact(source = reportFile, artifactName = "metrics.performance.json")
  }

  fun publishMetricsToYourCI(startResult: IDEStartResult): MetricsPublisherExample {
    publishAction(startResult, getCollectedMetrics(startResult))
    return this
  }
}

class MetricsCollectionTest {

  @Test
  fun testCollectionMetrics() {
    val metricPrefixes = listOf("jps.", "workspaceModel.", "FilePageCache.")
    val spanNames = listOf("project.opening")

    val context = Starter.newContext(CurrentTestMethod.get()!!.displayName, IdeaCommunityCases.GradleJitPackSimple)
      .prepareProjectCleanImport()

    val exitCommandChain = CommandChain().exitApp()
    val startResult = context.runIDE(commands = exitCommandChain, runTimeout = 4.minutes)

    val collectedMetrics = MetricsPublisherExample()
      // add span collector (from opentelemetry.json file that is located in the log directory)
      .addMetricsCollector(StarterTelemetrySpanCollector(spanNames = spanNames))
      // add meters collector (from .csv files that is located in log directory)
      .addMetricsCollector(
        StarterTelemetryMeterCollector(MetricsSelectionStrategy.SUM) {
          metricPrefixes.any { prefix -> it.key.startsWith(prefix) }
        })
      .publishMetricsToYourCI(startResult)
      .getCollectedMetrics(startResult)

    withClue("Collected metrics should not be empty") {
      collectedMetrics.shouldNotBeEmpty()
    }
  }
}