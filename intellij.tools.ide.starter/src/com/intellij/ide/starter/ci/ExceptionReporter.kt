package com.intellij.ide.starter.ci

import java.nio.file.Path

interface ExceptionReporter {

  fun report(rootErrorsDir: Path)
}
class NoopExceptionReporter: ExceptionReporter {
  override fun report(rootErrorsDir: Path) {
    //empty
  }
}