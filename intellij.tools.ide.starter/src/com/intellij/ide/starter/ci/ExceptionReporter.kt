package com.intellij.ide.starter.ci

import java.nio.file.Path

interface ExceptionReporter {

  fun report(rootErrorsDir: Path, metaData: LaunchMetaData)
}
class NoopExceptionReporter: ExceptionReporter {
  override fun report(rootErrorsDir: Path, metaData: LaunchMetaData) {
    //empty
  }
}

data class LaunchMetaData(val data: List<Pair<String, String>>)
