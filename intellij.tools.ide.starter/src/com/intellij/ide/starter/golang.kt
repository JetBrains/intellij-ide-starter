package com.intellij.ide.starter

import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.openapi.util.SystemInfo
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.HttpClient
import java.nio.file.Path

fun downloadGoSdk(version: String): Path {
  val os = when {
    SystemInfo.isWindows -> "windows"
    SystemInfo.isLinux -> "linux"
    SystemInfo.isMac -> "darwin"
    else -> error("Unknown OS")
  }
  val extension = if (SystemInfo.isWindows) ".zip" else ".tar.gz"
  val url = "https://cache-redirector.jetbrains.com/dl.google.com/go/go$version.$os-amd64$extension"
  val dirToDownload = GlobalPaths.instance.getCacheDirectoryFor("go-sdk/$version")
  val downloadedFile = dirToDownload.resolve("go$version.$os-amd64$extension")
  val goRoot = dirToDownload.resolve("go-roots")
  if (goRoot.toFile().exists()) {
    return goRoot.resolve("go")
  }

  HttpClient.download(url, downloadedFile)
  FileSystem.unpack(downloadedFile, goRoot)
  return goRoot.resolve("go")
}