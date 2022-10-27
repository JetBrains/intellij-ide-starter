package com.intellij.ide.starter.project

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.utils.Git
import org.kodein.di.instance
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Project, hosted as a Git repository
 */
data class GitProjectInfo(
  val sshRepositoryUrl: String,
  override val isReusable: Boolean = true,

  /**
   * Relative path inside Image file, where project home is located
   */
  override val testProjectImageRelPath: (Path) -> Path = { it }
) : ProjectInfoSpec {

  override fun downloadAndUnpackProject(): Path {
    val globalPaths by di.instance<GlobalPaths>()

    val projectsUnpacked = globalPaths.getCacheDirectoryFor("projects").resolve("unpacked")
    val projectHome = projectsUnpacked.resolve(sshRepositoryUrl.split("/").last().split(".git").first())

    if (projectHome.exists()) {
      Git.reset(projectHome)
      Git.pull(projectHome)
    }
    else {
      Git.clone(repoUrl = sshRepositoryUrl, destinationDir = projectHome)
    }

    val imagePath = projectHome.let(testProjectImageRelPath)
    return imagePath
  }
}