package com.intellij.ide.starter.community

import com.intellij.ide.starter.community.model.ReleaseInfo
import com.intellij.ide.starter.ide.IdeDownloader
import com.intellij.ide.starter.ide.installer.IdeInstallerFile
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.runner.SetupException
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.system.OS
import com.intellij.openapi.util.SystemInfo
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.exists

object PublicIdeDownloader : IdeDownloader {

  /** Filter release map: <ProductCode, List of releases> */
  private fun findSpecificRelease(releaseInfoMap: Map<String, List<ReleaseInfo>>,
                                  filteringParams: ProductInfoRequestParameters): ReleaseInfo {
    try {
      val sortedByDate = releaseInfoMap.values.first().sortedByDescending { it.date }

      val build = when {
        filteringParams.majorVersion.isNotBlank() -> sortedByDate.first { it.majorVersion == filteringParams.majorVersion}
        // find the latest release / eap, if no specific params were provided
        filteringParams.versionNumber.isBlank() && filteringParams.buildNumber.isBlank() -> sortedByDate.first()
        filteringParams.versionNumber.isNotBlank() -> sortedByDate.first { it.version == filteringParams.versionNumber }
        filteringParams.buildNumber.isNotBlank() -> sortedByDate.first { it.build == filteringParams.buildNumber }
        else -> null
      }

      if (build != null) {
        val expirationDate = build.date.plusDays(30)
        if (build.type == "eap" && expirationDate.isBefore(LocalDate.now())) {
          throw SetupException("EAP build ${build.build} expired on $expirationDate")
        }

        return build
      }
    }
    catch (e: Exception) {
      logError("Failed to find specific release by parameters $filteringParams")
      throw e
    }

    throw NoSuchElementException("Couldn't find specified release by parameters $filteringParams")
  }

  override fun downloadIdeInstaller(ideInfo: IdeInfo, installerDirectory: Path): IdeInstallerFile {
    val params = ProductInfoRequestParameters(type = ideInfo.productCode,
                                              snapshot = ideInfo.buildType,
                                              buildNumber = ideInfo.buildNumber,
                                              versionNumber = ideInfo.version)

    val releaseInfoMap = JetBrainsDataServiceClient.getReleases(params)
    if (releaseInfoMap.size != 1) throw RuntimeException("Only one product can be downloaded at once. Found ${releaseInfoMap.keys}")
    val possibleBuild: ReleaseInfo = findSpecificRelease(releaseInfoMap, params)

    val downloadLink: String = when (OS.CURRENT) {
      OS.Linux -> possibleBuild.downloads.linux!!.link
      OS.macOS -> {
        if (SystemInfo.OS_ARCH == "aarch64") possibleBuild.downloads.macM1!!.link // macM1
        else possibleBuild.downloads.mac!!.link
      }
      OS.Windows -> possibleBuild.downloads.windowsZip!!.link
      else -> throw RuntimeException("Unsupported OS ${OS.CURRENT}")
    }

    val installerFile = installerDirectory.resolve("${ideInfo.installerFilePrefix}-${possibleBuild.build}${ideInfo.installerFileExt}")

    if (!installerFile.exists()) {
      logOutput("Downloading $ideInfo from $downloadLink...")
      HttpClient.download(downloadLink, installerFile)
    }
    else logOutput("Installer file $installerFile already exists. Skipping download.")

    return IdeInstallerFile(installerFile, possibleBuild.build)
  }
}