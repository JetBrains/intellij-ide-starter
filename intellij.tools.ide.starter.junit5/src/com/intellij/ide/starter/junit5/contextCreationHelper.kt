package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.project.ProjectInfoSpec
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.Starter

class IDETestContextConfig {
  var project: ProjectInfoSpec = NoProject
  var systemProperties: Map<String, String> = mapOf()
}

/*
 A method helper that simplifies creating a new context for an integration UI test
 This method has one disadvantage:
 it is not possible to run a test on any IDE's version locally (release, EAP and so on)
 */
fun Starter.newContext(ideInfo: IdeInfo, testName: String = CurrentTestMethod.displayName(), configure: IDETestContextConfig.() -> Unit = {}): IDETestContext {
  val config = IDETestContextConfig().apply(configure)
  return newTestContainer().newContext(testName = testName, testCase = TestCase(ideInfo, config.project)).applyVMOptionsPatch {
    config.systemProperties.forEach {
      addSystemProperty(it.key, it.value)
    }
  }
}
