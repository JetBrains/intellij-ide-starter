package com.intellij.ide.starter.examples.indexing

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.examples.copyExistingConfig
import com.intellij.ide.starter.examples.copyExistingPlugins
import com.intellij.ide.starter.ide.IdeDownloader
import com.intellij.ide.starter.ide.IdeInstaller
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.ide.installer.ExistingIdeInstaller
import com.intellij.ide.starter.ide.installer.IdeInstallerFactory
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import com.intellij.tools.ide.performanceTesting.commands.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.nio.file.Paths

@Disabled("Requires local installation of IDE, configs and project")
class TestIndexing {
  init {
    di = DI {
      extend(di)
      //CONFIGURATION: change to the path to your IDE
      val pathToInstalledIDE =
        "/Users/maxim.kolmakov/Library/Application Support/JetBrains/Toolbox/apps/IDEA-U/ch-2/223.8617.56"
      //CONFIGURATION: comment line below if you don't want to use locally installed IDE and want to download one
      bindSingleton<IdeInstallerFactory>(overrides = true) {
        object : IdeInstallerFactory() {
          override fun createInstaller(ideInfo: IdeInfo, downloader: IdeDownloader): IdeInstaller {
            return ExistingIdeInstaller(Paths.get(pathToInstalledIDE))
          }
        }
      }
    }
  }

  @Test
  fun openProject() {
    //CONFIGURATION: specify required IDE version to download
    //if you want to use local version, don't change and make sure that no code in init{} section is commented
    //provide path to preconfigured project
    val testCase = TestCase(IdeProductProvider.IU, LocalProjectInfo(Paths.get("/Users/maxim.kolmakov/IdeaProjects/bazel")))
      .useRelease("2022.3.2")
    //provide path to config
    val config = Paths.get("/Users/maxim.kolmakov/Library/Application Support/JetBrains/IntelliJIdea2022.3")
    //provide path to plugins
    val plugins =
      Paths.get("/Users/maxim.kolmakov/Library/Application Support/JetBrains/Toolbox/apps/IDEA-U/ch-2/223.8617.56/IntelliJ IDEA.app.plugins")


    //SETUP
    val testContext = Starter.newContext(testName = "openProject", testCase = testCase)
      .copyExistingConfig(config)
      .copyExistingPlugins(plugins)
      .enableAsyncProfiler()
      .executeDuringIndexing()

    //TEST COMMANDS
    val commands = CommandChain().startProfile("indexing").waitForSmartMode().stopProfile().exitApp()

    //RUN
    testContext.runIDE(commands = commands) {
      addVMOptionsPatch {
        enableVerboseOpenTelemetry()
      }
    }
  }
}