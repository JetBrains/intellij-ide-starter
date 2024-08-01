package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IDETestContextFactory
import com.intellij.ide.starter.ide.LocalIDETestContextFactoryImpl
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.NoProject
import com.intellij.tools.ide.util.common.logOutput
import java.nio.file.Path

class RemdevIDETestContextFactoryImpl : IDETestContextFactory {
  override fun newContext(testName: String, testCase: TestCase<*>, preserveSystemDir: Boolean, projectHome: Path?, setupHooks: MutableList<IDETestContext.() -> IDETestContext>): IDETestContext {
    logOutput("Creating backend context")
    val backendContext = LocalIDETestContextFactoryImpl()
      .newContext(testName, testCase, preserveSystemDir, projectHome, setupHooks)

    logOutput("Creating frontend context")
    val frontendTestCase = backendContext.frontendTestCase
    val frontendContext = LocalIDETestContextFactoryImpl()
      .newContext(testName, frontendTestCase, preserveSystemDir, if (frontendTestCase.projectInfo is NoProject) null else backendContext.resolvedProjectHome, setupHooks)

    return IDERemDevTestContext.from(backendContext, frontendContext)
  }
}