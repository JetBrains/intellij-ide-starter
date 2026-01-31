import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.ui.components.button
import com.intellij.driver.sdk.ui.components.dialog
import com.intellij.driver.sdk.ui.components.ideFrame
import com.intellij.driver.sdk.ui.components.waitForNoOpenedDialogs
import com.intellij.driver.sdk.ui.components.welcomeScreen
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.driver.driver.remoteDev.RemDevDriverRunner
import com.intellij.ide.starter.driver.engine.DriverRunner
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.runner.RemDevTestContainer
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.runner.TestContainer
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.minutes
import org.kodein.di.DI
import org.kodein.di.bindProvider
import java.io.File
import kotlin.booleanArrayOf
import kotlin.time.Duration.Companion.seconds

class PluginTest {

  /**
   * Test to verify that the Demo plugin (built from sources) is installed in the IDE.
   *
   * This test does the following:
   * - Creates a new test context with specified settings.
   * - Installs the plugin from a given file path.
   * - Launches the IDE along with its test driver.
   * - Accesses the welcome screen to navigate to the Installed Plugins section.
   * - Asserts that the expected plugin is correctly installed and visible in the list.
   */
  @Test
  fun simpleTest() {
    Starter.newContext("testExample", TestCase(IdeProductProvider.IC, NoProject).withVersion("2024.3")).apply {
      val pathToPlugin = System.getProperty("path.to.build.plugin")
      PluginConfigurator(this).installPluginFromFolder(File(pathToPlugin))
    }.runIdeWithDriver().useDriverAndCloseIde {
      welcomeScreen {
        clickPlugins()
        x { byAccessibleName("Installed") }.click()
        shouldBe("Plugin is installed") {
          x {
            and(
              byVisibleText("Demo"),
              byJavaClass("javax.swing.JLabel")
            )
          }.present()
        }

      }
    }
  }

  /**
   * Executes a parameterized test to verify that the Demo plugin works with or without a split-mode (remote development env).
   *
   * This test performs the following actions:
   * - Configures a custom dependency injection setup when split mode is enabled.
   * - Initializes a test context with specific settings, including IDE product and project details.
   * - Installs a plugin from a specified path.
   * - Executes an action provided by the plugin and verifies its outcome in the IDE's UI dialog.
   *
   * @param splitMode Indicates whether split mode should be enabled during the test execution.
   */
  @ParameterizedTest(name = "split-mode={0}")
  @ValueSource(booleans = [false, true])
  fun oneMoreTest(splitMode: Boolean) {
    if (splitMode) {
      di = DI {
        extend(di)
        bindProvider<TestContainer>(overrides = true) { TestContainer.newInstance<RemDevTestContainer>() }
        bindProvider<DriverRunner> { RemDevDriverRunner() }
      }
    }

    Starter.newContext(
      "oneMoreTest-" + if (splitMode) "split-mode" else "no-split-mode",
      TestCase(
        IdeProductProvider.WS,
        GitHubProject.fromGithub(branchName = "master", repoRelativeUrl = "JetBrains/ij-perf-report-aggregator")
      ).useEAP()
    ).apply {
      setLicense(System.getenv("LICENSE_KEY"))
      val pathToPlugin = System.getProperty("path.to.build.plugin")
      PluginConfigurator(this).installPluginFromFolder(File(pathToPlugin))
    }.runIdeWithDriver().useDriverAndCloseIde {
      waitForIndicators(5.minutes)
      openFile("package.json")
      ideFrame {
        // This action processed by Demo plugin
        invokeAction("ShowDialogAction", now = false)

        dialog(title = "Test Dialog") {
          button("OK").click()
        }

        waitForNoOpenedDialogs()
      }
    }
  }
}