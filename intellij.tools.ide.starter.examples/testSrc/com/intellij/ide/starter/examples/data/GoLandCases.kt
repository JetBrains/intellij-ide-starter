package com.intellij.ide.starter.examples.data

import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.project.TestCaseTemplate

object GoLandCases : TestCaseTemplate(IdeProductProvider.GO) {

  val Gvisor = withProject(
    GitHubProject.fromGithub(
      branchName = "master",
      repoRelativeUrl = "google/gvisor"
    )
  )

}