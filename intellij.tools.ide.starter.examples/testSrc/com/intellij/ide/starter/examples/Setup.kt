package com.intellij.ide.starter.examples

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IdeDownloader
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.ide.installer.ExistingIdeInstaller
import com.intellij.ide.starter.ide.installer.IdeInstallerFactory
import com.intellij.ide.starter.ide.installer.IdeInstallerFile
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.runner.Starter
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.nio.file.Path
import java.nio.file.Paths

// To be used only for local test runs
class Setup {

  companion object {
    init {
      di = DI {
        extend(di)
        bindSingleton<IdeInstallerFactory>(overrides = true) { createInstallerFactory() }
        bindSingleton<IdeDownloader>(overrides = true) { IdeNotDownloader(Paths.get(PATH_TO_INSTALLED_IDE)) }
      }
    }

    private fun createInstallerFactory() = object : IdeInstallerFactory() {
      override fun createInstaller(ideInfo: IdeInfo, downloader: IdeDownloader) =
        ExistingIdeInstaller(Paths.get(PATH_TO_INSTALLED_IDE))
    }

    // This helpers are required to run locally installed IDE instead of downloading one
    class IdeNotDownloader(private val installer: Path) : IdeDownloader {
      override fun downloadIdeInstaller(ideInfo: IdeInfo, installerDirectory: Path): IdeInstallerFile {
        return IdeInstallerFile(installer, "locally-installed-ide")
      }
    }

    private val LOCAL_PATH = System.getProperty("user.home")

    // ----- CONFIGURATION SECTION ----
    private val PATH_TO_INSTALLED_IDE = "/Applications/IntelliJ IDEA.app"
    private val CONFIG_PATH = Paths.get("$LOCAL_PATH/Library/Application Support/JetBrains/IdeaIU2025.3")
    private val PLUGINS_PATH = Paths.get("$CONFIG_PATH/plugins")

    val IDE_TYPE = IdeProductProvider.IU

    /**
     * Sets up the test context by initializing the necessary objects and configurations.
     */
    fun setupTestContext(): IDETestContext {
      val testCase = TestCase(
        IDE_TYPE,
        GitHubProject.fromGithub(
          branchName = "master",
          repoRelativeUrl = "Perfecto-Quantum/Quantum-Starter-Kit.git",
          commitHash = "1dc6128c115cb41fc442c088174e81f63406fad5"
        )
      )
      return Starter
        .newContext(testName = "PerformanceTest", testCase = testCase)
        .copyExistingConfig(CONFIG_PATH)
        .updateGeneralSettings()
        // Uncomment this line if you have custom plugins in use
        //.copyExistingPlugins(PLUGINS_PATH)
        .addProjectToTrustedLocations()
        .enableAsyncProfiler()
    }

  }

}