package com.intellij.ide.starter.utils

import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.time.Duration.Companion.minutes

object Git {
  val branch by lazy { getShortBranchName() }
  val localBranch by lazy { getLocalGitBranch() }
  val getDefaultBranch by lazy { localBranch.substringBefore(".")}

  @Throws(IOException::class, InterruptedException::class)
  private fun getLocalGitBranch(): String {
    val stdout = ExecOutputRedirect.ToString()

    ProcessExecutor(
      "git-local-branch-get",
      workDir = null,
      timeout = 1.minutes,
      args = listOf("git", "rev-parse", "--abbrev-ref", "HEAD"),
      stdoutRedirect = stdout
    ).start()

    return stdout.read().trim()
  }

  private fun getShortBranchName(): String {
    val master = "master"
    return runCatching {
      when (val branch = getLocalGitBranch().substringBefore(".")) {
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

  fun clone(repoUrl: String, destinationDir: Path, branchName: String = "") {
    val cmdName = "git-clone"

    val arguments = mutableListOf("git", "clone", repoUrl, destinationDir.nameWithoutExtension)
    if (branchName.isNotEmpty()) arguments.addAll(listOf("-b", branchName))

    ProcessExecutor(
      presentableName = cmdName,
      workDir = destinationDir.parent.toAbsolutePath(),
      timeout = 10.minutes,
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
}

