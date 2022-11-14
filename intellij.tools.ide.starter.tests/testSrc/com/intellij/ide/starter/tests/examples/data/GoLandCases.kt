package com.intellij.ide.starter.tests.examples.data

import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.project.RemoteArchiveProjectInfo
import kotlin.io.path.div

object GoLandCases : TestCaseTemplate(IdeProductProvider.GO) {

  val LightEditor = getTemplate()

  val Kratos = getTemplate().copy(
    projectInfo = RemoteArchiveProjectInfo(
      testProjectURL = "https://github.com/go-kratos/kratos/archive/refs/heads/main.zip",
      testProjectImageRelPath = { it / "kratos-main" }
    )
  )
}