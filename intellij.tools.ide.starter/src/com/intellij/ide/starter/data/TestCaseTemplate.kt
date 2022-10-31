package com.intellij.ide.starter.data

import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase

abstract class TestCaseTemplate(val ideInfo: IdeInfo) {
  fun getTemplate() = TestCase(ideInfo = ideInfo)
}

