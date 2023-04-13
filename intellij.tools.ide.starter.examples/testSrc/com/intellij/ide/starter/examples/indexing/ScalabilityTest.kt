package com.intellij.ide.starter.examples.indexing

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.ide.command.CommandChain
import com.intellij.ide.starter.junit5.JUnit5StarterAssistant
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.report.publisher.ReportPublisher
import com.intellij.ide.starter.report.publisher.impl.ConsoleTestResultPublisher
import com.intellij.ide.starter.runner.TestContainerImpl
import com.intellij.metricsCollector.metrics.extractIndexingMetrics
import com.jetbrains.performancePlugin.commands.chain.exitApp
import com.jetbrains.performancePlugin.commands.chain.waitForSmartMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.ExtendWith
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.nio.file.Paths

@ExtendWith(JUnit5StarterAssistant::class)
class ScalabilityTest {
  private lateinit var testInfo: TestInfo
  private lateinit var context: TestContainerImpl

  //this can be removed in the next release
  companion object {
    init {
      di = DI {
        extend(di)
        bindSingleton<List<ReportPublisher>>(overrides = true) { listOf(ConsoleTestResultPublisher) }
      }
    }
  }

  private fun IDETestContext.setActiveProcessorCount(count: Int): IDETestContext =
    this.addVMOptionsPatch {
      this.addLine("-XX:ActiveProcessorCount=$count", "-XX:ActiveProcessorCount")
    }

  @Test
  fun scalabilityOfIndexingTest() {
    //CONFIGURATION
    //provide path to your local project
    val testCase = TestCase(IdeProductProvider.IU, LocalProjectInfo(Paths.get("/Users/maxim.kolmakov/IdeaProjects/coroutines")))
    //provide the required release
      .useRelease("2023.1")

    //provide path to config with valid license
    val config = Paths.get("/Users/maxim.kolmakov/Library/Application Support/JetBrains/IntelliJIdea2023.1")
    //provide path to plugins if needed
    val plugins = Paths.get("/Users/maxim.kolmakov/Library/Application Support/JetBrains/Toolbox/apps/IDEA-U/ch-1/231.8109.175/IntelliJ IDEA 2023.1 EAP.app.plugins")

    val processorCounts = listOf(1, 2, 4, 8, 16, 32, 64)
    val results = mutableMapOf<Int, List<Long>>()
    for (processorCount in processorCounts) {
      val context = context
        .initializeTestContext("${testInfo.hyphenateWithClass()}_$processorCount", testCase)
        .copyExistingConfig(config)
        .copyExistingPlugins(plugins)
        .setActiveProcessorCount(processorCount)

      val commands = CommandChain().waitForSmartMode().exitApp()

      val result = context.runIDE(commands = commands)
      val indexingMetrics = extractIndexingMetrics(result)
      results[processorCount] = listOf(indexingMetrics.totalIndexingTime, indexingMetrics.totalScanFilesTime)
    }
    println("\n###RESULTS###")
    println("Number of processors, Total Indexing Time, Total Scanning Time")
    for (result in results) {
      println(result.key.toString() + "," + result.value.joinToString(","))
    }
    println("######\n")
  }
}