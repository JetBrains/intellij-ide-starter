package com.intellij.ide.starter.sdk

import com.intellij.ide.starter.sdk.SdkObject
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkItem
import java.nio.file.Path

data class JdkDownloadItem(
  val jdk: JdkItem,
  private val download: () -> JdkItemPaths
) {
  private val installJdk = lazy { download() }
  val home: Path get() = installJdk.value.homePath
  val installPath: Path get() = installJdk.value.installPath
  private val majorVersion = jdk.jdkMajorVersion

  fun toSdk(sdkName: String) = SdkObject(
    sdkName = sdkName,
    sdkType = "JavaSDK",
    sdkPath = home,
  )

  fun toSdk(): SdkObject = toSdk(majorVersion.toString())

  override fun equals(other: Any?) = other is JdkDownloadItem && other.jdk == jdk
  override fun hashCode() = jdk.hashCode()
  override fun toString(): String = "JdkDownloadItem(${jdk.fullPresentationText})"
}