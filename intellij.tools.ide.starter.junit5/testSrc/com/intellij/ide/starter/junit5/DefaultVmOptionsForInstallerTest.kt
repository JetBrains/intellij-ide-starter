package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.runner.TestContainerImpl
import examples.data.GoLandCases
import io.kotest.assertions.withClue
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.booleans.shouldBeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readLines

@ExtendWith(JUnit5StarterAssistant::class)
class DefaultVmOptionsForInstallerTest {
  private lateinit var testInfo: TestInfo
  private lateinit var container: TestContainerImpl

  @Test
  fun `default VM options should be copied from installer to starter VMOptions`() {
    val context = container
      .initializeTestContext(testName = testInfo.hyphenateWithClass(), testCase = GoLandCases.CliProject)

    val defaultVmOptionsFile = context.ide.installationPath.resolve("bin").listDirectoryEntries(glob = "*.vmoptions").single()

    defaultVmOptionsFile.readLines().shouldForAll { vmOption ->
      withClue("Default VMOption $vmOption should be copied to starter VMOptions") {
        context.ide.vmOptions.hasOption(vmOption).shouldBeTrue()
      }
    }
  }
}