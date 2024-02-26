package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.project.ProjectInfoSpec
import com.intellij.ide.starter.runner.CurrentDisplayName
import com.intellij.ide.starter.runner.Starter

fun Starter.newContext(ideInfo: IdeInfo, project: ProjectInfoSpec = NoProject, systemProperties: Map<String, String> = mapOf()): IDETestContext =
  newTestContainer().newContext(testName = CurrentDisplayName.displayName(), testCase = TestCase(ideInfo, project)).applyVMOptionsPatch {
    systemProperties.forEach {
      addSystemProperty(it.key, it.value)
    }
  }

fun Starter.newContext(testCase: TestCase<*>, systemProperties: Map<String, String> = mapOf()): IDETestContext =
  newTestContainer().newContext(testName = CurrentDisplayName.displayName(), testCase = testCase).applyVMOptionsPatch {
    systemProperties.forEach {
      addSystemProperty(it.key, it.value)
    }
  }

