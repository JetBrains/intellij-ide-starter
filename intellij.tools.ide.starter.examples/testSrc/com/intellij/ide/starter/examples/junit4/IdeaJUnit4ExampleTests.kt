package com.intellij.ide.starter.examples.junit4

import com.intellij.ide.starter.examples.data.TestCases
import com.intellij.ide.starter.junit4.hyphenateWithClass
import com.intellij.ide.starter.junit4.initJUnit4StarterRule
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName

class IdeaJUnit4ExampleTests {
  @get:Rule
  val testContextFactory = initJUnit4StarterRule()

  @Test
  fun `open gradle project on the latest EAP IJ Community`() {
    val context = testContextFactory
      .initializeTestContext(CurrentTestMethod.hyphenateWithClass(), TestCases.IC.GradleJitPackSimple)
      .prepareProjectCleanImport()
      .setSharedIndexesDownload(enable = true)

    context.runIDE(commands = CommandChain().exitApp())
  }

  @Test
  fun `open gradle project on the latest Release IJ Community`() {
    val context = testContextFactory
      .initializeTestContext(CurrentTestMethod.hyphenateWithClass(), TestCases.IC.GradleJitPackSimple.useRelease())
      .prepareProjectCleanImport()
      .setSharedIndexesDownload(enable = true)

    context.runIDE(commands = CommandChain().exitApp())
  }
}