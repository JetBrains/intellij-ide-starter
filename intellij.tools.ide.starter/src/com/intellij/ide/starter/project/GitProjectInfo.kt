package com.intellij.ide.starter.project

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.utils.Git
import com.intellij.ide.starter.utils.logError
import org.kodein.di.instance
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Project, hosted as a Git repository
 */
data class GitProjectInfo(
  /**
   * SSH or HTTPS url.
   * Bear in mind, that it should be either available without authentication or you should have an ssh keys on the machine.
   */
  val repositoryUrl: String,

  val commitHash: String = "",

  /**
   * Branch name is a must to specify because there is no easy way to decipher it from git.
   * And if you're switching between branches in tests you may encounter problem,
   * that tests can execute on the branch, that you're not expected.
   */
  val branchName: String,

  override val isReusable: Boolean = false,
  override val downloadTimeout: Duration = 10.minutes,

  /**
   * Relative path inside Image file, where project home is located
   */
  val projectHomeRelativePath: (Path) -> Path = { it },
  override val configureProjectBeforeUse: (IDETestContext) -> Unit = {},
  private val description: String = ""
) : ProjectInfoSpec {

  val repositoryRootDir: Path
    get() {
      val globalPaths by di.instance<GlobalPaths>()
      val projectsUnpacked = globalPaths.getCacheDirectoryFor("projects").resolve("unpacked").createDirectories()
      return projectsUnpacked.resolve(repositoryUrl.split("/").last().split(".git").first())
    }

  val projectPath: Path
    get() = repositoryRootDir.let(projectHomeRelativePath)

  private fun cloneRepo(projectHome: Path) {
    Git.clone(repoUrl = repositoryUrl, destinationDir = projectHome, branchName = branchName, timeout = downloadTimeout)
  }

  private fun setupRepositoryState(projectHome: Path) {
    if (!isReusable) {
      Git.reset(repositoryDirectory = projectHome)
      Git.clean(projectHome)
    }
    val localBranch = Git.getLocalGitBranch(projectHome)
    if (branchName.isNotEmpty() && localBranch != branchName) {
      Git.checkout(repositoryDirectory = projectHome, branchName = branchName)
    }

    if (commitHash.isNotEmpty() && Git.getLocalCurrentCommitHash(projectHome) != commitHash) {
      val hasCommit = Git.getLocalBranches(projectHome, commitHash).contains(branchName)
      if (!hasCommit) Git.pull(projectHome)
      Git.reset(repositoryDirectory = projectHome, commitHash = commitHash)
    }
  }

  private fun isGitMetadataExist(repoRoot: Path) = repoRoot.listDirectoryEntries(".git").isNotEmpty()

  private fun projectRootDirectorySetup(repoRoot: Path) = when {
    !repoRoot.exists() -> cloneRepo(repoRoot)

    repoRoot.exists() -> {
      when {
        // for some reason repository is corrupted => delete directory with repo completely for clean checkout
        !isGitMetadataExist(repoRoot) -> {
          repoRoot.toFile().deleteRecursively()
          Unit
        }

        // simple remove everything, except .git directory - it will speed up subsequent git clean / reset (no need to redownload repo)
        !isReusable -> repoRoot.listDirectoryEntries().filterNot { it.endsWith(".git") }
          .forEach { it.toFile().deleteRecursively() }

        else -> Unit
      }
    }

    else -> Unit
  }

  override fun downloadAndUnpackProject(): Path {
    try {
      projectRootDirectorySetup(repositoryRootDir)
      setupRepositoryState(repositoryRootDir)
    }
    catch (_: Exception) {
      logError("Failed to setup the test project git repository state as: $this")
      logError("Trying one more time from clean checkout")

      repositoryRootDir.toFile().deleteRecursively()

      cloneRepo(repositoryRootDir)
      setupRepositoryState(repositoryRootDir)
    }

    return repositoryRootDir.let(projectHomeRelativePath)
  }

  fun onCommit(commitHash: String): GitProjectInfo = copy(commitHash = commitHash)

  fun onBranch(branchName: String): GitProjectInfo = copy(branchName = branchName)

  override fun getDescription(): String {
    return description
  }
}