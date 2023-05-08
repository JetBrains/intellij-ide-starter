package com.intellij.ide.starter.project

import java.nio.file.Path
import kotlin.time.Duration

interface ProjectInfoSpec {
  val isReusable: Boolean
  val downloadTimeout: Duration

  /**
   * Relative path inside archive where project home is located
   */
  val projectHomeRelativePath: (Path) -> Path

  fun downloadAndUnpackProject(): Path?

  fun getDescription(): String = ""
}
