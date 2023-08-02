package com.intellij.ide.starter.ide

import com.intellij.ide.starter.community.PublicIdeDownloader
import com.intellij.ide.starter.community.model.BuildType
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.IdeProduct
import com.intellij.ide.starter.models.IdeProductImp
import org.kodein.di.DI
import org.kodein.di.bindSingleton

/**
 * Determines behaviour for downloading/using EAP / RELEASE builds
 * Might be customized/overriden via DI
 */
interface IdeInfoConfigurable {
  fun useRC(ideInfo: IdeInfo): IdeInfo = ideInfo.copy(buildType = BuildType.RC.type)

  fun useEAP(ideInfo: IdeInfo): IdeInfo = ideInfo.copy(buildType = BuildType.EAP.type)

  /**
   * [buildNumber] - EAP build number to download
   * E.g: "222.3244.1"
   * If empty - the latest EAP will be downloaded.
   * [Downloads for IDEA Ultimate](https://www.jetbrains.com/idea/download/other.html)
   **/
  fun useEAP(ideInfo: IdeInfo, buildNumber: String = ""): IdeInfo = useEAP(ideInfo).run { withBuildNumber(this, buildNumber) }

  fun useRelease(ideInfo: IdeInfo): IdeInfo = ideInfo.copy(buildType = BuildType.RELEASE.type)

  /**
   * [version] - Release version to download
   * E.g: "2022.1.2"
   * If empty - the latest release will be downloaded.
   * [Downloads for IDEA Ultimate](https://www.jetbrains.com/idea/download/other.html)
   **/
  fun useRelease(ideInfo: IdeInfo, version: String = ""): IdeInfo = useRelease(ideInfo).run { withVersion(this, version) }

  /** E.g: "222.3244.1" */
  fun withBuildNumber(ideInfo: IdeInfo, buildNumber: String): IdeInfo = ideInfo.copy(buildNumber = buildNumber)

  /** E.g: "2022.1.2" */
  fun withVersion(ideInfo: IdeInfo, version: String): IdeInfo = ideInfo.copy(version = version)

  /**
   * Hack to redirect workflow of IDE downloading back to use publicly available IDE releases
   */
  fun usePublicIdeDownloader() {
    di = DI {
      extend(di)
      bindSingleton<IdeProduct>(overrides = true) { IdeProductImp }
      bindSingleton<IdeDownloader>(overrides = true) { PublicIdeDownloader }
    }
  }

  /**
   * Reset DI implementation to the desired default logic of downloading IDE's
   */
  fun resetDIToDefaultDownloading()
}