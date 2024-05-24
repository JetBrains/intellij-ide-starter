package com.intellij.ide.starter.examples.junit5

import com.intellij.ide.starter.examples.data.TestCases
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.Starter
import com.intellij.tools.ide.metrics.collector.starter.collector.getMetricsFromSpanAndChildren
import com.intellij.tools.ide.metrics.collector.telemetry.SpanFilter
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import com.intellij.tools.ide.performanceTesting.commands.inspectCode
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

class IdeaJUnit5ExampleTest {
  @Test
  fun openGradleJitPack() {

    val testContext = Starter
      .newContext(CurrentTestMethod.hyphenateWithClass(), TestCases.IC.GradleJitPackSimple)
      .prepareProjectCleanImport()

    val exitCommandChain = CommandChain().exitApp()

    testContext.runIDE(
      commands = exitCommandChain,
      launchName = "first run"
    )

    testContext.runIDE(
      commands = exitCommandChain,
      launchName = "second run"
    )
  }

  @Test
  fun openMavenProject() {

    val testContext = Starter
      .newContext(CurrentTestMethod.hyphenateWithClass(), TestCases.IC.MavenSimpleApp)
      .prepareProjectCleanImport()

    testContext.runIDE(commands = CommandChain().exitApp())
  }

  @Test
  @Disabled("Long running test (> 10 min)")
  fun inspectMavenProject() {
    val testContext = Starter
      .newContext(CurrentTestMethod.hyphenateWithClass(), TestCases.IC.MavenSimpleApp)

    val result = testContext.runIDE(commands = CommandChain().inspectCode().exitApp())
    getMetricsFromSpanAndChildren(result, SpanFilter.nameEquals("globalInspections")).forEach {
      println("Name: " + it.id.name)
      println("Value: " + it.value + "ms")
    }
  }

  @Test
  fun openPythonProjectInPyCharm() {
    val testContext = Starter
      .newContext(CurrentTestMethod.hyphenateWithClass(), TestCases.PY.PublicApis)
    testContext.runIDE(commands = CommandChain().exitApp())
  }

  @Test
  fun openGoProjectInGoLand() {
    val testContext = Starter
      .newContext(CurrentTestMethod.hyphenateWithClass(), TestCases.GO.Gvisor)
    testContext.runIDE(commands = CommandChain().exitApp())
  }
}