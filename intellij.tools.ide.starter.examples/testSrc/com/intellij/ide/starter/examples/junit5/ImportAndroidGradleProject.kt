package com.intellij.ide.starter.examples.junit5

import com.intellij.ide.starter.frameworks.AndroidFramework
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.sdk.JdkDownloaderFacade
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import com.intellij.tools.ide.starter.build.server.android.studio.AndroidStudio
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class ImportAndroidGradleProject {


  @Test
  @Disabled("There is a dialog on start of AndroidStudio which freezes the whole test")
  fun openGradleJitPack() {
    val testCase = TestCase(IdeInfo.AndroidStudio.copy(buildNumber = "2023.1.1.28"), GitHubProject.fromGithub(
      branchName = "master",
      repoRelativeUrl = "jitpack/gradle-simple.git")
    )

    val testContext = Starter.newContext(testName = "importAndroidProject", testCase = testCase)
      .prepareProjectCleanImport()
      .apply {
        withFramework<AndroidFramework>().setupAndroidSdkToProject(
          AndroidFramework.downloadLatestAndroidSdk(JdkDownloaderFacade.jdk11.home))
      }


    //IDE will wait till import and indexing is finished and exit after that
    testContext.runIDE(
      commands = CommandChain().exitApp(),
      launchName = "firstIdeRun"
    )
  }
}