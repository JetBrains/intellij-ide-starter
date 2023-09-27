package com.intellij.ide.starter.ide

import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.system.OsType
import java.nio.file.Path

interface InstalledIde {
  val vmOptions: VMOptions

  val build: String
  val os: OsType
  val productCode: String
  val isFromSources: Boolean

  /** Eg: /opt/REPO/intellij/out/perf-startup/cache/builds/GO-233.6745.304/GoLand-233.6745.304/ */
  val installationPath: Path

  /** Bundled plugins directory, if supported **/
  val bundledPluginsDir: Path?
    get() = null

  val patchedVMOptionsFile: Path?
    get() = null

  fun startConfig(vmOptions: VMOptions, logsDir: Path): IDEStartConfig

  fun resolveAndDownloadTheSameJDK(): Path

  fun isMajorVersionAtLeast(v: Int) = build.substringBefore(".").toIntOrNull()?.let { it >= v } ?: true
}