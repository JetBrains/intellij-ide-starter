package com.intellij.ide.starter

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.ide.starter.utils.getUpdateEnvVarsWithAddedPath
import com.intellij.ide.starter.utils.updatePathEnvVariable
import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.system.CpuArch
import com.intellij.util.text.SemVer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.minutes

fun downloadAndConfigureNodejs(version: String): Path {
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
  val nodePath = buildNodePath(nodejsRoot)

  if (nodejsRoot.toFile().exists()) {
    return nodePath
  }

  HttpClient.download(url, downloadedFile)
  FileSystem.unpack(downloadedFile, dirToDownload)
  enableCorepack(nodePath)
  return nodePath
}

fun installNodeModules(projectDir: Path, nodeVersion: String, packageManager: String, noFrozenLockFile: Boolean = false) {
  if(projectDir.resolve("node_modules").exists()) {
    logOutput("node_modules folder already exists")
    return
  }

  val stdout = ExecOutputRedirect.ToString()
  val nodejsRoot = getNodePathByVersion(nodeVersion)
  val packageManagerPath = getApplicationExecutablePath(nodejsRoot, packageManager)
  val args = mutableListOf("$packageManagerPath", "install")
  if (noFrozenLockFile) args += "--no-frozen-lockfile"

  ProcessExecutor(presentableName = "installing node modules",
                  projectDir,
                  timeout = 10.minutes,
                  args = args,
                  environmentVariables = getUpdateEnvVarsWithAddedPath(nodejsRoot),
                  stdoutRedirect = stdout
  ).start()
}

fun runBuild(projectDir: Path, nodeVersion: String, packageManager: String) {
  val nodejsRoot = getNodePathByVersion(nodeVersion)
  val packageManagerPath = getApplicationExecutablePath(nodejsRoot, packageManager)

  ProcessExecutor(presentableName = "running build script",
                  projectDir,
                  timeout = 5.minutes,
                  args = listOf("$packageManagerPath", "build"),
                  environmentVariables = getUpdateEnvVarsWithAddedPath(nodejsRoot)
  ).start()
}

fun IDETestContext.setUseTypesFromServer(value: Boolean) = applyVMOptionsPatch {
  addSystemProperty("typescript.compiler.evaluation", value.toString())
}

fun IDETestContext.updatePath(path: Path) = applyVMOptionsPatch {
  updatePathEnvVariable(path)
}

private fun buildNodePath(path: Path): Path {
  return if (SystemInfo.isWindows) path else path.resolve("bin")
}

private fun getApplicationExecutablePath(nodejsRoot: Path, application: String): Path {
  return if (SystemInfo.isWindows) nodejsRoot.resolve("$application.cmd") else nodejsRoot.resolve(application)
}

private fun enableCorepack(nodejsRoot: Path) {
  val corePackPath = getApplicationExecutablePath(nodejsRoot, "corepack")

  ProcessExecutor(presentableName = "corepack enable",
                  nodejsRoot,
                  timeout = 1.minutes,
                  args = listOf("$corePackPath", "enable"),
                  environmentVariables = getUpdateEnvVarsWithAddedPath(nodejsRoot)
  ).start()
}


private fun getNodePathByVersion(version: String): Path {
  val nodeJSDir = GlobalPaths.instance.getCacheDirectoryFor("nodejs")

  val matchingFolder = Files.list(nodeJSDir)
    .filter { Files.isDirectory(it) }
    .filter { it.fileName.toString().contains("node-v$version") }
    .toList()
    .first()

  return buildNodePath(matchingFolder)
}