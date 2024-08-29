package com.intellij.ide.starter.utils

import com.intellij.ide.starter.ci.teamcity.TeamCityClient
import com.intellij.ide.starter.ci.teamcity.TeamCityClient.guestAuthUri
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.openapi.util.io.findOrCreateDirectory
import com.intellij.openapi.util.io.findOrCreateFile
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.updater.Runner
import com.intellij.util.system.OS
import java.nio.file.Path
import kotlin.io.path.notExists
import kotlin.time.Duration.Companion.minutes

object UpdateIdeUtils {
  private val updaterJar: Path by lazy {
    val path = GlobalPaths.instance.getCacheDirectoryFor("updater")
    val jarName = "updater-full.jar"
    val jarPath = path.resolve(jarName)
    if (jarPath.notExists()) {
      val buildId = TeamCityClient
        .get(guestAuthUri.resolve("builds?locator=buildType:ijplatform_master_Idea_BuildUpdaterJar,branch:master,status:SUCCESS,state:(finished:true),count:1"))
        .fields().asSequence().first { it.key == "build" }.value
        .findValue("id").asText()

      TeamCityClient.downloadArtifact(buildId, jarName, jarPath.toFile())

      if (OS.CURRENT != OS.Windows) {
        ProcessExecutor(
          "give-permissions-to-updater",
          path,
          1.minutes,
          args = listOf("chmod", "a+rx", jarName),
          stdoutRedirect = ExecOutputRedirect.ToString(),
          stderrRedirect = ExecOutputRedirect.ToString()
        ).start()
      }
    }
    return@lazy jarPath
  }

  private fun getRunnerClassPaths(): String {
    val moduleName = "intellij.tools.updater"
    val clazz = Runner::class.java
    val absolutePath = clazz.classLoader
      .getResource(clazz.name.replace('.', '/') + ".class")!!.toString()
      .removePrefix("file:")
    val moduleIndex = absolutePath.indexOf(moduleName)
    val moduleRootPath = absolutePath.substring(0, moduleIndex + moduleName.length)

    return moduleRootPath
  }

  private fun performUpdaterAction(actionParams: List<String>, logsDir: Path) {
    val classpath = getRunnerClassPaths()
    val updaterLogsDir = logsDir.resolve("updater-${System.currentTimeMillis()}").findOrCreateDirectory()
    updaterLogsDir.resolve("idea_updater.log").findOrCreateFile()
    updaterLogsDir.resolve("idea_updater_error.log").findOrCreateFile()
    val args = mutableListOf("java",
                             "-Xmx25000m",
                             "-Didea.updater.log=$updaterLogsDir",
                             "-cp", classpath,
                             Runner::class.java.name) + actionParams

    ProcessExecutor("updater-${actionParams[0]}-patch",
                    null,
                    args = args,
                    stdoutRedirect = ExecOutputRedirect.ToString(),
                    stderrRedirect = ExecOutputRedirect.ToString()).start()
  }

  fun buildPatch(oldIde: InstalledIde, newIde: InstalledIde, patchOutputPath: String, logsDir: Path) {
    logOutput("Create patch from ${oldIde.build} to ${newIde.build}")
    performUpdaterAction(listOf(
      "create",
      oldIde.build,
      newIde.build,
      oldIde.installationPath.toString(),
      newIde.installationPath.toString(),
      patchOutputPath,
      "--jar=$updaterJar"), logsDir)
  }

  fun applyPatch(idePath: String, patchPath: String, logsDir: Path) {
    performUpdaterAction(listOf("apply",
                                idePath,
                                "--jar=$patchPath"), logsDir)
  }
}