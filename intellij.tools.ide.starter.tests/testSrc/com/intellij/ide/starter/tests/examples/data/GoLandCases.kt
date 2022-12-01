package com.intellij.ide.starter.tests.examples.data

import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.project.TestCaseTemplate

object GoLandCases : TestCaseTemplate(IdeProductProvider.GO) {
  val CliProject = getTemplate().withProject(
    GitHubProject.fromGithub(repoRelativeUrl = "/urfave/cli.git")
  )
}