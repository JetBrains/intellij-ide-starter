package com.intellij.ide.starter.examples.indexing

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.examples.data.TestCases
import com.intellij.ide.starter.ide.command.CommandChain
import com.intellij.ide.starter.junit5.JUnit5StarterAssistant
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.report.publisher.ReportPublisher
import com.intellij.ide.starter.report.publisher.impl.ConsoleTestResultPublisher
import com.intellij.ide.starter.runner.TestContainerImpl
import com.intellij.metricsCollector.metrics.extractIndexingMetrics
import com.intellij.metricsCollector.publishing.publishIndexingMetrics
import com.jetbrains.performancePlugin.commands.chain.exitApp
import com.jetbrains.performancePlugin.commands.chain.waitForSmartMode
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.kodein.di.DI
import org.kodein.di.bindSingleton

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

  @ParameterizedTest
  @ValueSource(ints = [1, 2, 4, 8, 16, 32, 64])
  fun indexingGradleJitPackSimpleProject(processorCount: Int) {
    val context = context
      .initializeTestContext("${testInfo.hyphenateWithClass()}_$processorCount", TestCases.IC.GradleJitPackSimple)
      .setActiveProcessorCount(processorCount)

    val commands = CommandChain()
      //.startProfile("indexing")
      .waitForSmartMode()
      //.stopProfile()
      .exitApp()

    val result = context.runIDE(commands = commands)
    extractIndexingMetrics(result).publishIndexingMetrics()
  }
}