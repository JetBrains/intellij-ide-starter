package com.intellij.tools.plugin.checker.data

import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.tools.ide.starter.build.server.phpstorm.PhpStorm

object PhpStormCases : TestCaseTemplate(IdeInfo.PhpStorm) {
  val LaravelFramework = withProject(
    GitHubProject.fromGithub(repoRelativeUrl = "laravel/framework", branchName = "master")
  )
}