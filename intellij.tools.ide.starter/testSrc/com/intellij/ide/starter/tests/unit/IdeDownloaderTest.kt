package com.intellij.ide.starter.tests.unit

import com.intellij.ide.starter.community.ProductInfoRequestParameters
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IdeDownloader
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.junit5.JUnit5StarterAssistant
import com.intellij.ide.starter.tests.examples.data.TestCases
import io.kotest.assertions.withClue
import io.kotest.matchers.longs.shouldNotBeZero
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.kodein.di.instance
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.fileSize

@ExtendWith(JUnit5StarterAssistant::class)
class IdeDownloaderTest {
  @TempDir
  lateinit var testDirectory: Path

  @Test
  fun completeParametersSetForIdeDownloaderShouldBeCorrectlyCreated() {
    ProductInfoRequestParameters(type = "IC",
                                 snapshot = "eap",
                                 majorVersion = "222",
                                 buildNumber = "10.20")
      .toUriQuery()
      .shouldBe("?code=IC&type=eap")
  }

  @Test
  fun defaultParameterForIdeDownloaderCreation() {
    ProductInfoRequestParameters(type = "IU")
      .toUriQuery()
      .shouldBe("?code=IU&type=release")
  }

  @Test
  @Timeout(value = 4, unit = TimeUnit.MINUTES)
  fun downloadLatestEapInstaller() {
    val downloader by di.instance<IdeDownloader>()

    val installer = downloader.downloadIdeInstaller(ideInfo = IdeProductProvider.IC, testDirectory)

    withClue("EAP installer should be downloaded successfully") {
      installer.installerFile.shouldExist()
      installer.installerFile.fileSize().shouldNotBeZero()
    }
  }

  @Test
  @Timeout(value = 4, unit = TimeUnit.MINUTES)
  fun downloadLatestReleaseInstaller() {
    val downloader by di.instance<IdeDownloader>()

    val testCase = TestCases.IC.GradleJitPackSimple.useRelease()
    val installer = downloader.downloadIdeInstaller(ideInfo = testCase.ideInfo, testDirectory)

    withClue("Release installer should be downloaded successfully") {
      installer.installerFile.shouldExist()
      installer.installerFile.fileSize().shouldNotBeZero()
    }
  }

  @Test
  @Timeout(value = 4, unit = TimeUnit.MINUTES)
  fun downloadSpecificReleaseInstaller() {
    val downloader by di.instance<IdeDownloader>()

    val testCase = TestCases.IC.GradleJitPackSimple.useRelease(version = "2022.1.2")
    val installer = downloader.downloadIdeInstaller(ideInfo = testCase.ideInfo, testDirectory)

    withClue("Specific release installer should be downloaded successfully") {
      installer.installerFile.shouldExist()
      installer.installerFile.fileSize().shouldNotBeZero()
    }
  }

  @Disabled("EAP build will be removed after certain period of time")
  @Test
  @Timeout(value = 4, unit = TimeUnit.MINUTES)
  fun downloadSpecificEapInstaller() {
    val downloader by di.instance<IdeDownloader>()

    val testCase = TestCases.IC.GradleJitPackSimple.useEAP(buildNumber = "222.3244.4")
    val installer = downloader.downloadIdeInstaller(ideInfo = testCase.ideInfo, testDirectory)

    withClue("Specific EAP installer should be downloaded successfully") {
      installer.installerFile.shouldExist()
      installer.installerFile.fileSize().shouldNotBeZero()
    }
  }

  @Test
  @Timeout(value = 4, unit = TimeUnit.MINUTES)
  fun downloadLatestEAP() {
    val downloader by di.instance<IdeDownloader>()

    val testCase = TestCases.IC.GradleJitPackSimple.useEAP()
    val installer = downloader.downloadIdeInstaller(ideInfo = testCase.ideInfo, testDirectory)

    withClue("Latest EAP installer should be downloaded successfully") {
      installer.installerFile.shouldExist()
      installer.installerFile.fileSize().shouldNotBeZero()
    }
  }
}