package com.intellij.ide.starter.examples.data

import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.tools.ide.starter.build.server.pycharm.PyCharm

object PyCharmCases : TestCaseTemplate(IdeInfo.PyCharm) {

  val PublicApis = withProject(
    GitHubProject.fromGithub(
      branchName = "master",
      repoRelativeUrl = "public-apis/public-apis"
    )
  )

}