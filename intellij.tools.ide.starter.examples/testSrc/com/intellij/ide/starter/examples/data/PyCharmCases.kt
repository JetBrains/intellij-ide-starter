package com.intellij.ide.starter.examples.data

import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.project.TestCaseTemplate

object PyCharmCases : TestCaseTemplate(IdeProductProvider.PY) {

  val PublicApis = withProject(
    GitHubProject.fromGithub(
      branchName = "master",
      repoRelativeUrl = "public-apis/public-apis"
    )
  )

}