package com.intellij.tools.plugin.checker.data

import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.project.TestCaseTemplate

object PhpStormCases : TestCaseTemplate(IdeProductProvider.PS) {
  val LaravelFramework = withProject(
    GitHubProject.fromGithub(repoRelativeUrl = "laravel/framework")
  )
}