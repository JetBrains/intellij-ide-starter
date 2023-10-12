package com.intellij.ide.starter.examples.indexing

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IdeDistributionFactory
import com.intellij.ide.starter.ide.IdeInstaller
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.ide.installer.IdeInstallerFile
import com.intellij.ide.starter.junit5.JUnit5StarterAssistant
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.TestContainerImpl
import com.intellij.tools.ide.performanceTesting.commands.*
import org.apache.commons.io.FileUtils
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.kodein.di.DI
import org.kodein.di.bindFactory
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.div

@ExtendWith(JUnit5StarterAssistant::class)
@Disabled("Requires local installation of IDE, configs and project")
class TestIndexing {
  private lateinit var container: TestContainerImpl

  class IdeLocalInstaller(private val installer: Path) : IdeInstaller {
    override fun install(ideInfo: IdeInfo): Pair<String, InstalledIde> {
      val ideInstaller = IdeInstallerFile(installer, "locally-installed-ide")
      val installDir = di.direct.instance<GlobalPaths>()
                         .getCacheDirectoryFor("builds") / "${ideInfo.productCode}-${ideInstaller.buildNumber}"
      FileUtils.deleteDirectory(installDir.toFile())
      FileUtils.copyDirectory(installer.toFile(), installDir.toFile())
      return Pair(
        ideInstaller.buildNumber,
        IdeDistributionFactory.installIDE(installDir.toFile(), ideInfo.executableFileName)
      )
    }
  }

  init {
    di = DI {
      extend(di)
      //CONFIGURATION: change to the path to your IDE
      val pathToInstalledIDE =
        "/Users/maxim.kolmakov/Library/Application Support/JetBrains/Toolbox/apps/IDEA-U/ch-2/223.8617.56"
      //CONFIGURATION: comment line below if you don't want to use locally installed IDE and want to download one
      bindFactory<IdeInfo, IdeInstaller>(overrides = true) { _ -> IdeLocalInstaller(Paths.get(pathToInstalledIDE)) }
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
    val testContext = container
      .initializeTestContext(testName = "openProject", testCase = testCase)
      .copyExistingConfig(config)
      .copyExistingPlugins(plugins)
      .enableVerboseOpenTelemetry()
      .enableAsyncProfiler()
      .executeDuringIndexing()

    //TEST COMMANDS
    val commands = CommandChain().startProfile("indexing").waitForSmartMode().stopProfile().exitApp()

    //RUN
    testContext.runIDE(commands = commands)
  }
}