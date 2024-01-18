package com.intellij.ide.starter

import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.util.system.CpuArch
import java.nio.file.Path
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.text.SemVer
import com.intellij.ide.starter.utils.FileSystem

fun downloadNodejs(version: String): Path {
  val arch = when {
    SystemInfo.isMac && CpuArch.isIntel64() -> "darwin-x64"
    SystemInfo.isMac && CpuArch.isArm64() -> {
      if (SemVer.parseFromText(version)?.isGreaterOrEqualThan(16, 0, 0) == true)
        "darwin-arm64"
      else
        error(
          "Platform ${SystemInfo.OS_NAME} @ ${CpuArch.CURRENT} is supported in version 16.0.0 or higher. Requested version: $version")
    }
    SystemInfo.isLinux && CpuArch.isIntel64() -> "linux-x64"
    SystemInfo.isLinux && CpuArch.isArm64() -> "linux-arm64"
    SystemInfo.isWindows && CpuArch.isIntel64() -> "win-x64"
    else -> error("Platform ${SystemInfo.OS_NAME} @ ${CpuArch.CURRENT} is not supported")
  }
  val extension = if (SystemInfo.isWindows) ".zip" else ".tar.gz"
  val fileNameWithoutExt = "node-v$version-$arch"
  val url = "https://nodejs.org/dist/v$version/$fileNameWithoutExt$extension"
  val dirToDownload = GlobalPaths.instance.getCacheDirectoryFor("nodejs")
  val downloadedFile = dirToDownload.resolve("$fileNameWithoutExt$extension")
  val nodejsRoot = dirToDownload.resolve(fileNameWithoutExt)

  if (nodejsRoot.toFile().exists()) {
    return buildNodePath(nodejsRoot)
  }

  HttpClient.download(url, downloadedFile)
  FileSystem.unpack(downloadedFile, dirToDownload)
  return buildNodePath(nodejsRoot)
}

private fun buildNodePath(path: Path): Path {
  return if (SystemInfo.isWindows) path else path.resolve("bin")
}