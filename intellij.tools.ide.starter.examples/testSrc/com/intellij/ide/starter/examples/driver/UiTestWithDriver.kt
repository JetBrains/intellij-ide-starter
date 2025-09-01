package com.intellij.ide.starter.examples.driver

import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.editor
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.structureToolWindow
import com.intellij.driver.sdk.ui.components.common.toolwindows.projectView
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.driver.driver.remoteDev.RemDevDriverRunner
import com.intellij.ide.starter.driver.engine.DriverRunner
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.RemDevTestContainer
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.runner.TestContainer
import com.intellij.ide.starter.sdk.JdkDownloaderFacade.jdk11
import com.intellij.ide.starter.sdk.JdkDownloaderFacade.jdk21
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.kodein.di.DI
import org.kodein.di.bindProvider
import kotlin.time.Duration.Companion.minutes

class UiTestWithDriver {

  /**
   * Opens the editor from the project view, navigates to a specific line in the editor,
   * and inserts a comment line at the caret position.
   *
   * @param splitMode Determines the mode of execution. If true, the test runs on a remote development environment
   * with IDE backend and frontend running on the same host.
   */
  @ParameterizedTest(name = "split-mode={0}")
  @ValueSource(booleans = [true])
  fun openEditorFromStructureViewEnterCommentLine(splitMode: Boolean) {
    if (splitMode) {
      di = DI {
        extend(di)
        bindProvider<TestContainer<*>>(overrides = true) { TestContainer.newInstance<RemDevTestContainer>() }
        bindProvider<DriverRunner> { RemDevDriverRunner() }
      }
    }

    val testContext = Starter
      .newContext(CurrentTestMethod.hyphenateWithClass(), TestCase(IdeProductProvider.IU, GitHubProject.fromGithub(
        branchName = "master",
        repoRelativeUrl = "Perfecto-Quantum/Quantum-Starter-Kit.git",
        commitHash = "1dc6128c115cb41fc442c088174e81f63406fad5"
      )))
      .setupSdk(jdk21.toSdk())
      .setLicense(System.getenv("LICENSE_KEY"))
      .prepareProjectCleanImport()

    testContext.runIdeWithDriver().useDriverAndCloseIde {
      ideFrame {
        waitForIndicators(5.minutes)
        leftToolWindowToolbar.projectButton
          .open()

        projectView {
          projectViewTree
            .waitFound()
            .doubleClickPath("Quantum-Starter-Kit", "src", "main", "java", "com.quantum", "pages", "GooglePage", fullMatch = false)
        }

        codeEditor().click()

        codeEditor().apply {
          waitFound()
          goToLine(28)

          // private QAFExtendedWebElement {caret}searchOption;
          assertEquals(28, getCaretLine()) { "Cursor at the wrong line" }

          // private QAFExtendedWebElement searchOption;{caret}
          goToPosition(28, 48)
          keyboard {
            enter()
            typeText("// comment line 123")
          }

          assertTrue(text.contains("// comment line 123")) { "Editor doesn't contain the entered text" }
        }
      }
    }
  }
}