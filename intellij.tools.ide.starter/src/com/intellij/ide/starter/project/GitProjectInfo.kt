package com.intellij.ide.starter.project

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.utils.Git
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.tools.ide.util.common.logError
import org.kodein.di.instance
import java.nio.file.Path
import kotlin.io.path.*
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

  /** If empty - latest */
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
   * Set to true if you test don't need full VCS history and branches and project doesn't use submodules.
   */
  val shallow: Boolean = false,

  /**
   * Relative path inside Image file, where project home is located
   */
  val projectHomeRelativePath: (Path) -> Path = { it },
  override val configureProjectBeforeUse: (IDETestContext) -> Unit = {},
  private val description: String = "",
) : ProjectInfoSpec {

  val repositoryRootDir: Path
    get() {
      val globalPaths by di.instance<GlobalPaths>()

      // TODO: https://youtrack.jetbrains.com/issue/AT-2013/Eel-in-Starter-Make-GitProjectInfo-and-Git-aware-of-target-eel
      // as of now just use local cache directory for git project
      val projectsUnpacked = globalPaths.localCacheDirectory.resolve("projects").resolve("unpacked").createDirectories()

      return projectsUnpacked.resolve(repositoryUrl.split("/").last().split(".git").first())
    }

  // TODO: Remove this after https://youtrack.jetbrains.com/issue/AT-2013/Eel-in-Starter-Make-GitProjectInfo-and-Git-aware-of-target-eel
  // after setting up project we should return "real" directory path (not local) to use in remote target (Docker, WSL, etc)
  private val remoteRepositoryRootDir: Path
    get() {
      val globalPaths by di.instance<GlobalPaths>()
      val projectsUnpacked = globalPaths.cacheDirForProjects.resolve("unpacked").createDirectories()
      return projectsUnpacked.resolve(repositoryUrl.split("/").last().split(".git").first())
    }

  val projectPath: Path
    get() = repositoryRootDir.let(projectHomeRelativePath)

  private fun cloneRepo(projectHome: Path) {
    Git.clone(repoUrl = repositoryUrl, destinationDir = projectHome, branchName = branchName, shallow = shallow, timeout = downloadTimeout)
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

  @OptIn(ExperimentalPathApi::class)
  private fun projectRootDirectorySetup(repoRoot: Path) = when {
    !repoRoot.exists() -> cloneRepo(repoRoot)

    repoRoot.exists() -> {
      when {
        // for some reason the repository is corrupted => delete directory with repo completely for clean checkout
        !isGitMetadataExist(repoRoot) -> {
          repoRoot.deleteRecursively()
          Unit
        }

        // simple remove everything, except the.git directory - it will speed up subsequent git clean / reset (no need to redownload repo)
        !isReusable -> repoRoot.listDirectoryEntries().filterNot { it.endsWith(".git") }
          .forEach { it.deleteRecursively() }

        else -> Unit
      }
    }

    else -> Unit
  }

  @OptIn(ExperimentalPathApi::class)
  override fun downloadAndUnpackProject(): Path {
    try {
      projectRootDirectorySetup(repositoryRootDir)
      setupRepositoryState(repositoryRootDir)
    }
    catch (ex: Exception) {
      logError(buildString {
        appendLine("Failed to setup the test project git repository state as: $this")
        appendLine("Trying one more time from clean checkout")
      }, ex)

      runCatching { repositoryRootDir.deleteRecursively() }.getOrLogException { logError("Failed to delete $repositoryRootDir", it) }

      cloneRepo(repositoryRootDir)
      setupRepositoryState(repositoryRootDir)
    }

    // checkout directory locally, but start
    return remoteRepositoryRootDir.let(projectHomeRelativePath)
  }

  fun onCommit(commitHash: String): GitProjectInfo = copy(commitHash = commitHash)

  fun onBranch(branchName: String): GitProjectInfo = copy(branchName = branchName)

  override fun getDescription(): String {
    return description
  }
}