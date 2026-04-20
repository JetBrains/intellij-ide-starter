package com.intellij.tools.plugin.checker.data

import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.tools.plugin.checker.tests.IdeProductImpl

object PhpStormCases : TestCaseTemplate(IdeProductImpl.PS) {
  val LaravelFramework = withProject(
    GitHubProject.fromGithub(repoRelativeUrl = "laravel/framework", branchName = "master")
  )
}