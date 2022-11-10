package com.intellij.ide.starter.tests.examples.data

import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.project.RemoteArchiveProjectInfo
import kotlin.io.path.div

object  IdeaUltimateCases : TestCaseTemplate(IdeProductProvider.IU) {
  val IntelliJCommunityProject = getTemplate().withProject(
    RemoteArchiveProjectInfo(
      projectURL = "https://github.com/JetBrains/intellij-community/archive/master.zip",
      projectHomeRelativePath = { it / "intellij-community-master" }
    )
  )
}