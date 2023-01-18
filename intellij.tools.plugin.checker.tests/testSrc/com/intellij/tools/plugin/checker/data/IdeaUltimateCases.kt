package com.intellij.tools.plugin.checker.data

import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.project.RemoteArchiveProjectInfo
import kotlin.io.path.div

object IdeaUltimateCases : TestCaseTemplate(IdeProductProvider.IU) {

  val GradleJitPackSimple = getTemplate().withProject(
    RemoteArchiveProjectInfo(
      projectURL = "https://github.com/jitpack/gradle-simple/archive/refs/heads/master.zip",
      projectHomeRelativePath = { it / "gradle-simple-master" }
    )
  )

  val MavenSimpleApp = getTemplate().withProject(
    RemoteArchiveProjectInfo(
      projectURL = "https://github.com/jenkins-docs/simple-java-maven-app/archive/refs/heads/master.zip",
      projectHomeRelativePath = { it / "simple-java-maven-app-master" }
    )
  )

  val IntelliJCommunityProject = getTemplate().withProject(
    RemoteArchiveProjectInfo(
      projectURL = "https://github.com/JetBrains/intellij-community/archive/master.zip",
      projectHomeRelativePath = { it / "intellij-community-master" }
    )
  )
}