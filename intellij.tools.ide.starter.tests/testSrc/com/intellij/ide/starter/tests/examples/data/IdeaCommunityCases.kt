package com.intellij.ide.starter.tests.examples.data

import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.project.RemoteArchiveProjectInfo
import kotlin.io.path.div

object IdeaCommunityCases : TestCaseTemplate(IdeProductProvider.IC) {

  val GradleJitPackSimple = getTemplate().withProject(
    RemoteArchiveProjectInfo(
      testProjectURL = "https://github.com/jitpack/gradle-simple/archive/refs/heads/master.zip",
      testProjectImageRelPath = { it / "gradle-simple-master" }
    )
  )

  val MavenSimpleApp = getTemplate().withProject(
    RemoteArchiveProjectInfo(
      testProjectURL = "https://github.com/jenkins-docs/simple-java-maven-app/archive/refs/heads/master.zip",
      testProjectImageRelPath = { it / "simple-java-maven-app-master" }
    )
  )
}