package com.intellij.ide.starter.project

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.FileSystem.isDirUpToDate
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.ide.starter.utils.logOutput
import org.kodein.di.instance
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Project stored on remote server as an archive
 */
data class RemoteArchiveProjectInfo(
  val projectURL: String,
  override val isReusable: Boolean = true,
  override val downloadTimeout: Duration = 10.minutes,
  override val configureProjectBeforeUse: (IDETestContext) -> Unit = {},
  private val description: String = ""
) : ProjectInfoSpec {

  private var subFolder: String = ""

  private fun getTopMostFolderFromZip(zipFile: File): String = ZipFile(zipFile).entries().nextElement().name.split("/").first()

  fun withSubfolder(subFolder: String): RemoteArchiveProjectInfo {
    this.subFolder = subFolder
    return this
  }

  override fun downloadAndUnpackProject(): Path {
    val globalPaths by di.instance<GlobalPaths>()

    val projectsUnpacked = globalPaths.getCacheDirectoryFor("projects").resolve("unpacked").createDirectories()

    val zipFile = globalPaths.getCacheDirectoryFor("projects").resolve("zip").resolve(projectURL.transformUrlToZipName())

    HttpClient.downloadIfMissing(url = projectURL, targetFile = zipFile, timeout = downloadTimeout)
    val imagePath: Path = zipFile

    val projectHome = if (subFolder.isEmpty()) {
      projectsUnpacked / getTopMostFolderFromZip(zipFile.toFile())
    } else {
      projectsUnpacked / getTopMostFolderFromZip(zipFile.toFile()) / subFolder
    }


    if (!isReusable) {
      projectHome.toFile().deleteRecursively()
    }

    if (projectHome.isDirUpToDate()) {
      logOutput("Already unpacked project $projectHome will be used in the test")
      return projectHome
    }
    else {
      projectHome.toFile().deleteRecursively()
    }

    when {
      imagePath.isRegularFile() -> FileSystem.unpack(imagePath, projectsUnpacked)
      imagePath.isDirectory() -> imagePath.toFile().copyRecursively(projectsUnpacked.toFile(), overwrite = true)

      else -> error("$imagePath does not exist!")
    }

    return projectHome
  }

  private fun String.transformUrlToZipName(): String {
    return when (projectURL.contains("https://github.com")) {
      true -> {
        this.removePrefix("https://github.com/").split("/").let {
          it[0] + "_" + it[1] + ".zip"
        }
      }
      false -> projectURL.split("/").last()
    }
  }

  override fun getDescription(): String {
    return description
  }
}