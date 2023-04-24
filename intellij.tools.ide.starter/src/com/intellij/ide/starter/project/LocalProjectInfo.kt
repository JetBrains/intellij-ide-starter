package com.intellij.ide.starter.project

import java.nio.file.Path
import kotlin.io.path.notExists

/**
 * Project, that somehow already exist on filesystem.
 * So we cannot link it with any particular URL
 */
data class LocalProjectInfo(
  val projectDir: Path,
  override val isReusable: Boolean = true,
  override val projectHomeRelativePath: (Path) -> Path = { it },
  private val description: String = ""
) : ProjectInfoSpec {
  override fun downloadAndUnpackProject(): Path? {
    if (projectDir.notExists()) {
      return null
    }

    return projectDir.toAbsolutePath()
  }

  override fun getDescription(): String {
    return description
  }
}