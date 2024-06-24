package com.intellij.ide.starter.runner

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.TestCase
import org.kodein.di.provider

object Starter {
  fun newTestContainer(): TestContainer<*> {
    val testContainer: () -> TestContainer<*> by di.provider()
    return testContainer.invoke()
  }

  /**
   * @param baseContext - optional base context. If passed, some set up steps for the new context are omitted and we are re-using base context information.
   *                      For example - project unpacking
   */
  fun newContext(testName: String, testCase: TestCase<*>, preserveSystemDir: Boolean = false, baseContext: IDETestContext? = null): IDETestContext =
    newTestContainer().newContext(testName = testName, testCase = testCase, preserveSystemDir = preserveSystemDir, baseContext = baseContext)
}