package com.intellij.ide.starter.tests.examples.junit4

import com.intellij.ide.starter.ide.command.CommandChain
import com.intellij.ide.starter.junit4.hyphenateWithClass
import com.intellij.ide.starter.junit4.initJUnit4StarterRule
import com.intellij.ide.starter.tests.examples.data.TestCases
import com.jetbrains.performancePlugin.commands.chain.exitApp
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
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

  @Test
  fun `open gradle project on the latest RC IJ Community`() {
    val context = testContextFactory
      .initializeTestContext(testName.hyphenateWithClass(this::class), TestCases.IC.GradleJitPackSimple.useRC())
      .prepareProjectCleanImport()
      .setSharedIndexesDownload(enable = true)

    context.runIDE(commands = CommandChain().exitApp())
  }

  @Test
  fun `open Community project on the latest Release IJ Ultimate`() {
    val context = testContextFactory
      .initializeTestContext(testName.hyphenateWithClass(this::class), TestCases.IU.IntelliJCommunityProject.useRelease())
      //.setLicense(Paths.get(""))
      .setSharedIndexesDownload(enable = true)

    context.runIDE(commands = CommandChain().exitApp())
  }
}