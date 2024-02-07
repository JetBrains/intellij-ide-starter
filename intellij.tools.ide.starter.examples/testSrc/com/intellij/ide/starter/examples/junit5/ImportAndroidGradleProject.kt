
package com.intellij.ide.starter.examples.junit5

import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.junit5.JUnit5StarterAssistant
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.runner.Starter
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import com.intellij.tools.ide.performanceTesting.commands.importGradleProject
import com.intellij.tools.ide.performanceTesting.commands.waitForSmartMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(JUnit5StarterAssistant::class)
class ImportAndroidGradleProject {


  //@Test
  fun openGradleJitPack() {
    val testCase = TestCase(IdeProductProvider.AI.copy(buildNumber = "2023.1.1.28"), GitHubProject.fromGithub(
      branchName = "master",
      repoRelativeUrl = "/jitpack/gradle-simple.git"
    )
    )

    val testContext = Starter.newContext(testName = "openProject", testCase = testCase)
      .prepareProjectCleanImport()
      .setSharedIndexesDownload(enable = true)
      .apply {
        withFramework<AndroidFramework>().setupAndroidSdkToProject(
          AndroidFramework.downloadLatestAndroidSdk(JdkDownloaderFacade.jdk11.home))
      }
      .addProjectToTrustedLocations()


    //IDE will wait till import and indexing is finished and exit after that
    testContext.runIDE(
      commands = CommandChain().exitApp(),
      launchName = "first run"
    )
  }
}