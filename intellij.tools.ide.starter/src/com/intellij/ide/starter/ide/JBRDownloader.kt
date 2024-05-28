package com.intellij.ide.starter.ide

import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.HttpClient
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes
import kotlin.io.path.div

interface JBRDownloader {
  suspend fun downloadJbr(jbrFileName: String): Path
}

object StarterJBRDownloader : JBRDownloader {
  override suspend fun downloadJbr(jbrFileName: String): Path {
    val downloadUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/$jbrFileName"

    val jbrCacheDirectory = GlobalPaths.instance.getCacheDirectoryFor("jbr")
    val localFile = jbrCacheDirectory / jbrFileName
    val localDir = jbrCacheDirectory / jbrFileName.removeSuffix(".tar.gz")

    HttpClient.downloadIfMissing(downloadUrl, localFile, retries = 1, timeout = 5.minutes)

    FileSystem.unpackIfMissing(localFile, localDir)

    return localDir
  }

}