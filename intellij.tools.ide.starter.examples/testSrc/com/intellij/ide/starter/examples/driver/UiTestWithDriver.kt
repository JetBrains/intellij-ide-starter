package com.intellij.ide.starter.examples.driver

import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitVisible
import com.intellij.driver.sdk.ui.components.common.editor
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.structureToolWindow
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.driver.driver.remoteDev.RemDevDriverRunner
import com.intellij.ide.starter.driver.engine.DriverRunner
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.examples.data.TestCases
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.RemDevTestContainer
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.runner.TestContainer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.kodein.di.DI
import org.kodein.di.bindProvider
import kotlin.time.Duration.Companion.minutes

class UiTestWithDriver {

  /**
   * Opens the editor from the structure view of a specified file, navigates to a specific line in the editor,
   * and inserts a comment line at the caret position. Performs UI navigation and structure inspection steps
   * within the project and validates editor behavior after inserting the text.
   *
   * @param splitMode Determines the mode of execution. If true, the test runs on a remote development environment
   * with IDE backend and frontend running on the same host.
   */
  @ParameterizedTest(name = "split-mode={0}")
  @ValueSource(booleans = [false, true])
  fun openEditorFromStructureViewEnterCommentLine(splitMode: Boolean) {
    if (splitMode) {
      di = DI {
        extend(di)
        bindProvider<TestContainer<*>>(overrides = true) { TestContainer.newInstance<RemDevTestContainer>() }
        bindProvider<DriverRunner> { RemDevDriverRunner() }
      }
    }

    val testContext = Starter
      .newContext(CurrentTestMethod.hyphenateWithClass(), TestCases.IU.GradleQuantumSimple)
      .prepareProjectCleanImport()

    testContext.runIdeWithDriver().useDriverAndCloseIde {
      ideFrame {
        waitForIndicators(5.minutes)
        leftToolWindowToolbar.projectButton
          .open()

        projectViewTree
          .waitFound()
          .doubleClickPath("Quantum-Starter-Kit", "src", "main", "java", "com.quantum", "pages", "GooglePage", fullMatch = false)

        leftToolWindowToolbar.structureButton
          .click()
        structureToolWindow()
          .waitVisible()
          .waitOneContainsText("searchOption: QAFExtendedWebElement")
          .click()

        editor().apply {
          waitFound()

          // private QAFExtendedWebElement {caret}searchOption;
          assertTrue(getCaretLine() == 28) { "Cursor at the wrong line" }
          assertTrue(getCaretColumn() == 33) { "Cursor at the wrong column" }

          // private QAFExtendedWebElement searchOption;{caret}
          setCaretPosition(28, 48)
          keyboard {
            enter()
            enterText("// comment line 123")
          }

          assertTrue(text.contains("// comment line 123")) { "Editor doesn't contain the entered text" }
          // waitOneText { it.text.trim() == "comment line 123" }
        }
      }
    }
  }
}