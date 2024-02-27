package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.project.ProjectInfoSpec
import com.intellij.ide.starter.runner.CurrentDisplayName
import com.intellij.ide.starter.runner.Starter

class IDETestContextConfig {
  var project: ProjectInfoSpec = NoProject
  var systemProperties: Map<String, String> = mapOf()
}

fun Starter.newContext(ideInfo: IdeInfo, configure: IDETestContextConfig.() -> Unit = {}): IDETestContext {
  val config = IDETestContextConfig().apply(configure)
  return newTestContainer().newContext(testName = CurrentDisplayName.displayName(), testCase = TestCase(ideInfo, config.project)).applyVMOptionsPatch {
    config.systemProperties.forEach {
      addSystemProperty(it.key, it.value)
    }
  }
}
