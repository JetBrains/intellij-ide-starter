package com.intellij.ide.starter.project

import java.nio.file.Path

interface ProjectInfoSpec {
  val isReusable: Boolean

  /**
   * Relative path inside archive where project home is located
   */
  val projectHomeRelativePath: (Path) -> Path

  fun downloadAndUnpackProject(): Path?

  fun getDescription(): String = ""
}
