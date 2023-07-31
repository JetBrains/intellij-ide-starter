package com.intellij.ide.starter.ide

import com.intellij.ide.starter.di.di
import org.kodein.di.direct
import org.kodein.di.instance

class IDEHost(private val codeBuilder: (CodeInjector.() -> Unit)?, private val testContext: IDETestContext) {
  private val host by lazy { di.direct.instance<CodeInjector>() }

  fun setup() {
    val codeBuilder = codeBuilder
    if (codeBuilder != null) {
      host.codeBuilder()
      host.setup(testContext)
    }
  }

  fun tearDown() {
    codeBuilder?.let {
      host.tearDown(testContext)
    }
  }
}