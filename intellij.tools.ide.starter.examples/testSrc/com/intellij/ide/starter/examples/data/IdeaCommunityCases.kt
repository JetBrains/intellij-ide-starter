package com.intellij.ide.starter.examples.data

import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.project.TestCaseTemplate

object IdeaCommunityCases : TestCaseTemplate(IdeProductProvider.IC) {

  val GradleJitPackSimple = withProject(
    GitHubProject.fromGithub(
      branchName = "master",
      repoRelativeUrl = "jitpack/gradle-simple.git"
    )
  )

  val MavenSimpleApp = withProject(
    GitHubProject.fromGithub(
      branchName = "master",
      repoRelativeUrl = "jenkins-docs/simple-java-maven-app"
    )
  )
}