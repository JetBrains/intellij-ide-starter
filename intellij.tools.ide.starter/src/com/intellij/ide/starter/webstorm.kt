package com.intellij.ide.starter

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.util.system.CpuArch
import java.nio.file.Path
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.text.SemVer
import com.intellij.ide.starter.utils.FileSystem
import java.nio.file.Files
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
  val stdout = ExecOutputRedirect.ToString()
  val nodejsRoot = getNodePathByVersion(nodeVersion)
  val args = mutableListOf("$nodejsRoot/$packageManager", "install")
  if (noFrozenLockFile) args += "--no-frozen-lockfile"

  ProcessExecutor(presentableName = "installing node modules",
                  projectDir,
                  timeout = 5.minutes,
                  args = args,
                  environmentVariables = getUpdatedEnvVars(nodejsRoot),
                  stdoutRedirect = stdout
  ).start()
}

fun runBuild(projectDir: Path, nodeVersion: String, packageManager: String) {
  val nodejsRoot = getNodePathByVersion(nodeVersion)

  ProcessExecutor(presentableName = "running build script",
                  projectDir,
                  timeout = 5.minutes,
                  args = listOf("$nodejsRoot/$packageManager", "build"),
                  environmentVariables = getUpdatedEnvVars(nodejsRoot)
  ).start()
}

fun IDETestContext.enableNewTSEvaluator() = applyVMOptionsPatch {
  addSystemProperty("typescript.compiler.evaluation", "true")
}

fun IDETestContext.updatePath(path: Path) = applyVMOptionsPatch {
  val pathEnv = if (SystemInfo.isWindows) "Path" else "PATH"
  val envVars = getUpdatedEnvVars(path)[pathEnv]

  if (envVars != null) {
    withEnv(pathEnv, envVars)
  }
}

private fun buildNodePath(path: Path): Path {
  return if (SystemInfo.isWindows) path else path.resolve("bin")
}

private fun enableCorepack(nodejsRoot: Path) {
  ProcessExecutor(presentableName = "corepack enable",
                  nodejsRoot,
                  timeout = 1.minutes,
                  args = listOf("$nodejsRoot/corepack", "enable"),
                  environmentVariables = getUpdatedEnvVars(nodejsRoot)
  ).start()
}

private fun getUpdatedEnvVars(path: Path): Map<String, String> {
  val pathEnv = if (SystemInfo.isWindows) "Path" else "PATH"
  val pathSeparator = if (SystemInfo.isWindows) ";" else ":"
  val currentPath = System.getenv().getOrDefault(pathEnv, "")

  return System.getenv() + mapOf(pathEnv to "$currentPath$pathSeparator$path")
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