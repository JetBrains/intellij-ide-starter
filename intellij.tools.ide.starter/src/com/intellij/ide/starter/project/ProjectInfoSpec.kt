package com.intellij.ide.starter.project

import java.nio.file.Path

interface ProjectInfoSpec {
  val isReusable: Boolean

  /**
   * Relative path inside Image file, where project home is located
   */
  val testProjectImageRelPath: (Path) -> Path

  fun downloadAndUnpackProject(): Path?
}
