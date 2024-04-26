package com.intellij.ide.starter.examples.bazel

import com.intellij.ide.starter.examples.writeMetricsToCSV
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.runner.Starter
import com.intellij.tools.ide.metrics.collector.starter.metrics.extractIndexingMetrics
import com.intellij.tools.ide.metrics.collector.telemetry.SpanFilter
import com.intellij.tools.ide.metrics.collector.telemetry.getMetricsFromSpanAndChildren
import com.intellij.tools.ide.performanceTesting.commands.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.io.path.div

@Disabled("Bazel is not installed by default")
class BazelTest {
  @Test
  fun openBazelProject() {
    val testCase = TestCase(IdeProductProvider.IC, GitHubProject.fromGithub(
      branchName = "master",
      repoRelativeUrl = "MaXal/bazel-java"
    )).useRelease("2024.1")
    val context = Starter.newContext("openBazelProject", testCase).also {
      it.pluginConfigurator.installPluginFromPluginManager("com.google.idea.bazel.ijwb","2024.04.09.0.1-api-version-241")
    }

    val results = context.runIDE(commands = CommandChain())

    val metrics = getMetricsFromSpanAndChildren(
      (results.runContext.logsDir / "opentelemetry.json"), SpanFilter.nameContains("Progress: ")
    )
    val indexingMetrics = extractIndexingMetrics(results).getListOfIndexingMetrics()

    writeMetricsToCSV(results, metrics+indexingMetrics)
  }

}