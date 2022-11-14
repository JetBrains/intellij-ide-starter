package com.intellij.ide.starter.tests.examples.data

import com.intellij.ide.starter.data.TestCaseTemplate
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.project.RemoteArchiveProjectInfo
import kotlin.io.path.div

object  IdeaUltimateCases : TestCaseTemplate(IdeProductProvider.IU) {
  val IntelliJCommunityProject = getTemplate().withProject(
    RemoteArchiveProjectInfo(
      testProjectURL = "https://github.com/JetBrains/intellij-community/archive/master.zip",
      testProjectImageRelPath = { it / "intellij-community-master" }
    )
  )
}