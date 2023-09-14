package com.intellij.tools.plugin.checker.data

import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.project.RemoteArchiveProjectInfo
import com.intellij.ide.starter.project.TestCaseTemplate

object IdeaUltimateCases : TestCaseTemplate(IdeProductProvider.IU) {

  val GradleJitPackSimple = withProject(
    RemoteArchiveProjectInfo(
      projectURL = "https://github.com/jitpack/gradle-simple/archive/refs/heads/master.zip",
    )
  )

  val MavenSimpleApp = withProject(
    RemoteArchiveProjectInfo(
      projectURL = "https://github.com/jenkins-docs/simple-java-maven-app/archive/refs/heads/master.zip",
    )
  )

  val IntelliJCommunityProject = withProject(
    RemoteArchiveProjectInfo(
      projectURL = "https://github.com/JetBrains/intellij-community/archive/master.zip",
    )
  )
}