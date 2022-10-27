package com.intellij.ide.starter.project

import java.nio.file.Path

/**
 * Project, that somehow already exist on filesystem.
 * So we cannot link it with any particular URL
 */
data class LocalProjectInfo(
  val testProjectDir: Path,
  override val isReusable: Boolean = true,
  override val testProjectImageRelPath: (Path) -> Path = { it },
) : ProjectInfoSpec {
  override fun downloadAndUnpackProject(): Path? {
    if (!testProjectDir.toFile().exists()) {
      return null
    }

    return testProjectDir.toAbsolutePath()
  }
}