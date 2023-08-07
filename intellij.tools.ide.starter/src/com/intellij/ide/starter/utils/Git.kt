package com.intellij.ide.starter.utils

import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

object Git {
  val branch by lazy { getShortBranchName(Paths.get("")) }
  val localBranch by lazy { getLocalGitBranch(Paths.get("")) }
  val getDefaultBranch by lazy {
    when (val majorBranch = localBranch.substringBefore(".")) {
      "HEAD", "master" -> "master"
      else -> majorBranch
    }
  }

  @Throws(IOException::class, InterruptedException::class)
  fun getLocalGitBranch(repositoryDirectory: Path): String {
    val stdout = ExecOutputRedirect.ToString()

    ProcessExecutor(
      "git-local-branch-get",
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 1.minutes,
      args = listOf("git", "rev-parse", "--abbrev-ref", "HEAD"),
      stdoutRedirect = stdout
    ).start()

    return stdout.read().trim()
  }

  @Throws(IOException::class, InterruptedException::class)
  fun getLocalCurrentCommitHash(repositoryDirectory: Path): String {
    val stdout = ExecOutputRedirect.ToString()

    ProcessExecutor(
      "git-local-current-commit-get",
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 1.minutes,
      args = listOf("git", "rev-parse", "HEAD"),
      stdoutRedirect = stdout
    ).start()

    return stdout.read().trim()
  }

  private fun getShortBranchName(repositoryDirectory: Path): String {
    val master = "master"
    return runCatching {
      when (val branch = getLocalGitBranch(repositoryDirectory).substringBefore(".")) {
        master -> return branch
        else -> when (branch.toIntOrNull()) {
          null -> return master
          else -> return "IjPlatform$branch"
        }
      }
    }.getOrElse { master }
  }

  fun getRepoRoot(): Path {
    val stdout = ExecOutputRedirect.ToString()

    try {
      ProcessExecutor(
        "git-repo-root-get",
        workDir = null,
        timeout = 1.minutes,
        args = listOf("git", "rev-parse", "--show-toplevel", "HEAD"),
        stdoutRedirect = stdout
      ).start()
    }
    catch (e: Exception) {
      val workDir = Paths.get("").toAbsolutePath()
      logError("There is a problem in detecting git repo root. Trying to acquire working dir path: '$workDir'")
      return workDir
    }

    // Takes first line from output like this:
    // /opt/REPO/intellij
    // 1916dc2bef46b51cfb02ad9f7e87d12aa1aa9fdc
    return Path(stdout.read().split("\n").first().trim()).toAbsolutePath()
  }

  fun clone(repoUrl: String, destinationDir: Path, branchName: String = "", timeout: Duration = 10.minutes) {
    val cmdName = "git-clone"

    val arguments = mutableListOf("git", "clone", repoUrl, destinationDir.toString())
    if (branchName.isNotEmpty()) arguments.addAll(listOf("-b", branchName))

    ProcessExecutor(
      presentableName = cmdName,
      workDir = destinationDir.parent.toAbsolutePath(),
      timeout = timeout,
      args = arguments,
      stdoutRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      stderrRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      onlyEnrichExistedEnvVariables = true
    ).start()
  }

  fun status(projectDir: Path): Long {
    val arguments = mutableListOf("git", "-c", "core.fsmonitor=false", "status")

    val startTimer = System.currentTimeMillis()

    val execOutStatus = ExecOutputRedirect.ToString()
    ProcessExecutor(
      presentableName = "git-status",
      workDir = projectDir,
      timeout = 2.minutes,
      args = arguments,
      stdoutRedirect = execOutStatus,
      stderrRedirect = ExecOutputRedirect.ToString(),
      onlyEnrichExistedEnvVariables = true
    ).start()

    val endTimer = System.currentTimeMillis()
    val duration = endTimer - startTimer
    println("Git status took $duration")
    println("Git status output: ${execOutStatus.read()}")

    val execOutVersion = ExecOutputRedirect.ToString()
    ProcessExecutor(
      presentableName = "git-version",
      workDir = projectDir,
      timeout = 1.minutes,
      args = listOf("git", "--version"),
      stdoutRedirect = execOutVersion,
      stderrRedirect = ExecOutputRedirect.ToString(),
      onlyEnrichExistedEnvVariables = true
    ).start()

    println("Git version: ${execOutVersion.read()}")
    return duration
  }

  fun reset(repositoryDirectory: Path, commitHash: String = "") {
    val cmdName = "git-reset"

    val arguments = mutableListOf("git", "reset", "--hard")
    if (commitHash.isNotEmpty()) arguments.add(commitHash)

    ProcessExecutor(
      presentableName = cmdName,
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 10.minutes,
      args = arguments,
      stdoutRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      stderrRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      onlyEnrichExistedEnvVariables = true
    ).start()
  }

  fun clean(repositoryDirectory: Path) {
    val cmdName = "git-clean"

    ProcessExecutor(
      presentableName = cmdName,
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 10.minutes,
      args = listOf("git", "clean", "-fd"),
      stdoutRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      stderrRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      onlyEnrichExistedEnvVariables = true
    ).start()
  }


  fun checkout(repositoryDirectory: Path, branchName: String = "") {
    val cmdName = "git-checkout"

    val arguments = mutableListOf("git", "checkout")
    if (branchName.isNotEmpty()) arguments.add(branchName)

    ProcessExecutor(
      presentableName = cmdName,
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 10.minutes,
      args = arguments,
      stdoutRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      stderrRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      onlyEnrichExistedEnvVariables = true
    ).start()
  }

  fun pull(repositoryDirectory: Path) {
    val cmdName = "git-pull"

    ProcessExecutor(
      presentableName = cmdName,
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 10.minutes,
      args = listOf("git", "pull"),
      stdoutRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      stderrRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      onlyEnrichExistedEnvVariables = true
    ).start()
  }

  fun rebase(repositoryDirectory: Path, newBase: String = "master") {
    val cmdName = "git-rebase"

    ProcessExecutor(
      presentableName = cmdName,
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 10.minutes,
      args = listOf("git", "rebase", newBase),
      stdoutRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      stderrRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      onlyEnrichExistedEnvVariables = true
    ).start()
  }

  fun deleteBranch(workDir: Path, targetBranch: String) {
    val stdout = ExecOutputRedirect.ToString()
    ProcessExecutor(
      "git-delete-branch",
      workDir = workDir, timeout = 1.minutes,
      args = listOf("git", "branch", "-D", targetBranch),
      stdoutRedirect = stdout
    ).start()
  }

  fun pruneWorktree(pathToDir: Path) {
    val stdout = ExecOutputRedirect.ToString()
    ProcessExecutor(
      "git-prune-worktree",
      workDir = pathToDir, timeout = 1.minutes,
      args = listOf("git", "worktree", "prune"),
      stdoutRedirect = stdout
    ).start()
  }

  fun getStatus(pathToDir: Path): String {
    val stdout = ExecOutputRedirect.ToString()
    ProcessExecutor(
      "git-status",
      workDir = pathToDir, timeout = 1.minutes,
      args = listOf("git", "status"),
      stdoutRedirect = stdout
    ).start()
    return stdout.read()
  }

  /**
   * If commitHash is specified, only branches with this commit will be returned.
   * */
  fun getLocalBranches(repositoryDirectory: Path, commitHash: String = ""): List<String> {
    val arguments = mutableListOf("git", "for-each-ref", "--format='%(refname:short)'", "refs/heads/")
    if (commitHash.isNotEmpty()) arguments.addAll(listOf("--contains", commitHash))
    val stdout = ExecOutputRedirect.ToString()
    try {
      ProcessExecutor(
        "git-local-branches",
        workDir = repositoryDirectory.toAbsolutePath(),
        timeout = 1.minutes,
        args = arguments,
        stdoutRedirect = stdout
      ).start()
    }
    catch (e: IllegalStateException) {
      // == false - safe check
      // Exception "no such commit" is not error. Just don't have this commit
      if (e.message?.contains("no such commit") == false) throw IllegalStateException(e)
    }
    return stdout.read().trim().split("\n")
  }

  fun getRandomCommitInThePast(date: String, dir: Path): String {
    val stdout = ExecOutputRedirect.ToString()

    ProcessExecutor(
      "git-get-commits-on-specific-day",
      workDir = dir, timeout = 1.minutes,
      args = listOf("git", "log", "--after=\\\"$date 00:00\\\"", "--before=\\\"$date 23:59\\\"", "--format=%h"),
      stdoutRedirect = stdout
    ).start()

    val commits = stdout.read().split("\n")
    return commits[Random().nextInt(commits.size)]
  }

  fun getLastCommit(dir: Path, targetBranch: String = ""): String {
    val stdout = ExecOutputRedirect.ToString()
    val arguments = mutableListOf("git", "log")
    if (targetBranch.isNotEmpty()) arguments.addAll(listOf("-b", targetBranch))
    arguments.addAll(listOf("-n1", "--format=%h"))
    ProcessExecutor(
      "git-last-commit-get",
      workDir = dir,
      timeout = 1.minutes,
      args = arguments,
      stdoutRedirect = stdout
    ).start()

    return stdout.read().trim()
  }

  fun createWorktree(dir: Path, targetBranch: String, worktree_dir: String, commit: String = ""): String {
    val stdout = ExecOutputRedirect.ToString()
    val stderr = ExecOutputRedirect.ToString()
    val arguments = mutableListOf("git", "worktree", "add")
    val isBranchCreated = getLocalBranches(dir).contains(targetBranch)
    when (isBranchCreated) {
      false -> {
        arguments.addAll(listOf("-b", targetBranch, worktree_dir))
        if (commit.isNotEmpty()) arguments.add(commit)
      }
      true -> {
        pruneWorktree(dir)
        arguments.addAll(listOf(worktree_dir, targetBranch))
      }
    }
    ProcessExecutor(
      "git-create-worktree",
      workDir = dir,
      timeout = 10.minutes,
      args = arguments,
      stdoutRedirect = stdout,
      stderrRedirect = stderr
    ).start()

    return stdout.read().trim()
  }
}

