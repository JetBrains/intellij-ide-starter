package com.intellij.tools.ide.metrics.collector.starter

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.junit5.config.UseLatestDownloadedIdeBuild
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.Starter
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.starter.metrics.CommonMetrics
import com.intellij.tools.ide.metrics.collector.starter.metrics.GCLogAnalyzer
import com.intellij.tools.ide.metrics.collector.starter.publishing.MetricsPublisher
import com.intellij.tools.ide.metrics.collector.starter.publishing.addMeterCollector
import com.intellij.tools.ide.metrics.collector.starter.publishing.addSpanCollector
import com.intellij.tools.ide.metrics.collector.telemetry.SpanFilter
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import com.intellij.tools.ide.util.common.logOutput
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.kodein.di.DI
import org.kodein.di.bindProvider
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

class MetricsPublisherExample : MetricsPublisher<MetricsPublisherExample>() {
  override val publishAction: (IDEStartResult, List<PerformanceMetrics.Metric>) -> Unit = { ideStartResult, metrics ->
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
}

fun initDI() {
  di = DI {
    extend(di)
    importAll(metricsPublisherDI)

    // register your own metrics publisher
    bindProvider<MetricsPublisher<*>>(overrides = true) { MetricsPublisherExample() }
  }
}

@ExtendWith(UseLatestDownloadedIdeBuild::class)
class MetricsCollectionTest {
  companion object {
    // You can make that also as a JUnit5 TestListener that will enable it for all tests
    @JvmStatic
    @BeforeAll
    fun beforeAll(): Unit {
      initDI()
    }
  }

  @Test
  fun testCollectionMetrics() {
    val metricPrefixes = listOf("jps.", "workspaceModel.", "FilePageCache.")
    val spanNames = listOf("project.opening")

    val context = Starter.newContext(CurrentTestMethod.get()!!.displayName, IdeaCommunityCases.GradleJitPackSimple)
      .prepareProjectCleanImport()

    val exitCommandChain = CommandChain().exitApp()
    val startResult = context.runIDE(commands = exitCommandChain, runTimeout = 4.minutes)

    val collectedMetrics = MetricsPublisher.newInstance
      // add span collector (from opentelemetry.json file that is located in the log directory)
      .addSpanCollector(SpanFilter.nameInList(spanNames))
      // add meters collector (from .csv files that is located in log directory)
      .addMeterCollector(MetricsSelectionStrategy.SUM) {
        metricPrefixes.any { prefix -> it.name.startsWith(prefix) }
      }
      .publish(startResult)
      .collectMetrics(startResult)

    withClue("Collected metrics should not be empty") {
      collectedMetrics.shouldNotBeEmpty()
    }
  }
}