package com.intellij.ide.starter.project

import com.intellij.ide.starter.ide.IDETestContext
import java.nio.file.Path
import kotlin.time.Duration

interface ProjectInfoSpec {
  val isReusable: Boolean
  val downloadTimeout: Duration

  fun downloadAndUnpackProject(): Path?

  /**
   * Use this to tune/configure project before IDE start
   */
  val configureProjectBeforeUse: (IDETestContext) -> Unit

  fun getDescription(): String = ""
}
