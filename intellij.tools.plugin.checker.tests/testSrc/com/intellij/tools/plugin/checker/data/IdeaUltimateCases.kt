package com.intellij.tools.plugin.checker.data

import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.project.TestCaseTemplate

object IdeaUltimateCases : TestCaseTemplate(IdeProductProvider.IU) {

  val GradleJitPackSimple = withProject(
    GitHubProject.fromGithub(repoRelativeUrl = "jitpack/gradle-simple", branchName = "master")
  )

  val MavenSimpleApp = withProject(
    GitHubProject.fromGithub(
      branchName = "master",
      commitHash = "428464cd6d3cee7ae7f139395c25b5f719bc3c94",
      repoRelativeUrl = "jenkins-docs/simple-java-maven-app",
    )
  )

  val IntelliJCommunityProject = withProject(
    GitHubProject.fromGithub(
      branchName = "master",
      commitHash = "d0041ec5afd967d5129f360cfe92652d56a03024",
      repoRelativeUrl = "JetBrains/intellij-community",
    )
  )
}