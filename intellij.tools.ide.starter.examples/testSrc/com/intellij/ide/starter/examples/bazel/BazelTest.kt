package com.intellij.ide.starter.examples.bazel

import com.intellij.ide.starter.examples.getMetricsFromSpanAndChildren
import com.intellij.ide.starter.examples.writeMetricsToCSV
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.runner.Starter
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.starter.metrics.extractIndexingMetrics
import com.intellij.tools.ide.metrics.collector.telemetry.SpanFilter
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import com.intellij.util.indexing.diagnostic.dto.IndexingMetric
import com.intellij.util.indexing.diagnostic.dto.getListOfIndexingMetrics
import com.intellij.util.io.createParentDirectories
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.io.path.div

@Disabled("Bazel is not installed by default")
class BazelTest {
  @Test
  fun openBazelProject() {
    val testCase = TestCase(IdeProductProvider.IC, GitHubProject.fromGithub(
      branchName = "master",
      repoRelativeUrl = "https://github.com/bazelbuild/bazel.git"
    )).useRelease("2024.1")
    val context = Starter.newContext("openBazelProject", testCase).also {
      it.pluginConfigurator.installPluginFromPluginManager("com.google.idea.bazel.ijwb", "2024.04.09.0.1-api-version-241")
      (it.resolvedProjectHome / "tools" / "intellij" / ".managed.bazelproject").createParentDirectories().toFile().writeText("""
targets:
  //examples:srcs

directories:
  .

    """)
    }

    val results = context.runIDE(commands = CommandChain().exitApp())

    val metrics = getMetricsFromSpanAndChildren(results, SpanFilter.nameContains("Progress: "))
    val indexingMetrics = extractIndexingMetrics(results).getListOfIndexingMetrics().map {
      when (it) {
        is IndexingMetric.Duration -> PerformanceMetrics.newDuration(it.name, it.durationMillis)
        is IndexingMetric.Counter -> PerformanceMetrics.newCounter(it.name, it.value)
      }
    }

    writeMetricsToCSV(results, metrics + indexingMetrics)
  }

}