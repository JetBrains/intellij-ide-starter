package com.intellij.ide.starter.utils

import com.intellij.ide.starter.ide.IDETestContext

fun IDETestContext.setKotlinPluginMode(k2Mode: Boolean): IDETestContext {
  return applyVMOptionsPatch {
    addSystemProperty("idea.kotlin.plugin.use.k2", k2Mode.toString())
  }
}