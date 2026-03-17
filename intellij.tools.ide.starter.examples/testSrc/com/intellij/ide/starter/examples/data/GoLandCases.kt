package com.intellij.ide.starter.examples.data

import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.tools.ide.starter.build.server.goland.GoLand

object GoLandCases : TestCaseTemplate(IdeInfo.GoLand) {

  val Gvisor = withProject(
    GitHubProject.fromGithub(
      branchName = "master",
      repoRelativeUrl = "google/gvisor"
    )
  )

}