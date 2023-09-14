package com.intellij.tools.plugin.checker.data

import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.project.RemoteArchiveProjectInfo
import com.intellij.ide.starter.project.TestCaseTemplate
import kotlin.io.path.div

object PhpStormCases : TestCaseTemplate(IdeProductProvider.PS) {
  val LaravelFramework = withProject(
    RemoteArchiveProjectInfo(
      projectURL = "https://github.com/laravel/framework/archive/refs/heads/master.zip",
    )
  )
}