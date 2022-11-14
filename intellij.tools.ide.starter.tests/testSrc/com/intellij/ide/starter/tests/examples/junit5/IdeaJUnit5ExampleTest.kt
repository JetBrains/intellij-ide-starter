package com.intellij.ide.starter.tests.examples.junit5

import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.ide.command.CommandChain
import com.intellij.ide.starter.junit5.JUnit5StarterAssistant
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.project.GitProjectInfo
import com.intellij.ide.starter.runner.TestContainerImpl
import com.intellij.ide.starter.sdk.JdkDownloaderFacade
import com.intellij.ide.starter.sdk.JdkVersion
import com.intellij.ide.starter.tests.examples.data.TestCases
import com.intellij.metricsCollector.metrics.getOpenTelemetry
import com.jetbrains.performancePlugin.commands.chain.exitApp
import com.jetbrains.performancePlugin.commands.chain.inspectCode
import com.jetbrains.performancePlugin.commands.setupSdk
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(JUnit5StarterAssistant::class)
class IdeaJUnit5ExampleTest {

  // these properties will be injected via [JUnit5StarterAssistant]
  private lateinit var testInfo: TestInfo
  private lateinit var context: TestContainerImpl

  private val sdk17 by lazy {
    JdkDownloaderFacade.jdk17.toSdk(JdkVersion.JDK_17)
  }

  @Test
  fun openGradleJitPack() {

    val testContext = context
      .initializeTestContext(testInfo.hyphenateWithClass(), TestCases.IC.GradleJitPackSimple)
      .prepareProjectCleanImport()
      .skipIndicesInitialization()
      .setSharedIndexesDownload(enable = true)

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

    val testContext = context
      .initializeTestContext(testInfo.hyphenateWithClass(), TestCases.IC.MavenSimpleApp)
      .prepareProjectCleanImport()
      .skipIndicesInitialization()
      .setSharedIndexesDownload(enable = true)

    testContext.runIDE(commands = CommandChain().exitApp())
  }

  @Test
  @Disabled("Long running test (> 10 min)")
  fun inspectMavenProject() {
    val testContext = context
      .initializeTestContext(testInfo.hyphenateWithClass(), TestCases.IC.MavenSimpleApp)
      .collectOpenTelemetry()
      .setupSdk(sdk17)
      .setSharedIndexesDownload(enable = true)

    testContext.runIDE(commands = CommandChain().inspectCode().exitApp())

    getOpenTelemetry(testContext, "globalInspections").metrics.forEach {
      println("Name: " + it.n)
      println("Value: " + it.v + "ms")
    }
  }

  @Test
  fun usageGitRepoAsTestProject() {
    val matplotlibCheatSheetsGitProject = GitProjectInfo(
      repositoryUrl = "https://github.com/matplotlib/cheatsheets.git",
      branchName = "master"
    )

    val testContext = context
      .initializeTestContext(
        testName = testInfo.hyphenateWithClass(),
        testCase = object : TestCaseTemplate(IdeProductProvider.PY) {}.getTemplate().withProject(matplotlibCheatSheetsGitProject))
      .prepareProjectCleanImport()
      .skipIndicesInitialization()
      .setSharedIndexesDownload(enable = true)

    testContext.runIDE(commands = CommandChain().exitApp())
  }
}