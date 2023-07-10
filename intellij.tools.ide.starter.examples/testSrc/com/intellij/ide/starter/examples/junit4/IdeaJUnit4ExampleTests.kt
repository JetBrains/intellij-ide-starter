package com.intellij.ide.starter.examples.junit4

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.command.CommandChain
import com.intellij.ide.starter.junit4.hyphenateWithClass
import com.intellij.ide.starter.junit4.initJUnit4StarterRule
import com.intellij.ide.starter.report.publisher.ReportPublisher
import com.intellij.ide.starter.report.publisher.impl.ConsoleTestResultPublisher
import com.intellij.ide.starter.examples.data.TestCases
import com.jetbrains.performancePlugin.commands.chain.exitApp
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.kodein.di.DI
import org.kodein.di.bindSingleton

class IdeaJUnit4ExampleTests {
  @get:Rule
  val testName = TestName()

  @get:Rule
  val testContextFactory = initJUnit4StarterRule()

  @Test
  fun `open gradle project on the latest EAP IJ Community`() {
    val context = testContextFactory
      .initializeTestContext(testName.hyphenateWithClass(this::class), TestCases.IC.GradleJitPackSimple)
      .prepareProjectCleanImport()
      .setSharedIndexesDownload(enable = true)

    context.runIDE(commands = CommandChain().exitApp())
  }

  @Test
  fun `open gradle project on the latest Release IJ Community`() {
    val context = testContextFactory
      .initializeTestContext(testName.hyphenateWithClass(this::class), TestCases.IC.GradleJitPackSimple.useRelease())
      .prepareProjectCleanImport()
      .setSharedIndexesDownload(enable = true)

    context.runIDE(commands = CommandChain().exitApp())
  }
}