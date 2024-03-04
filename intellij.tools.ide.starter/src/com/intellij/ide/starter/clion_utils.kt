package com.intellij.ide.starter

import com.intellij.ide.starter.ide.IDETestContext

fun IDETestContext.disableCMakeOpenProjectWizard() =
  applyVMOptionsPatch { this.addSystemProperty("clion.skip.open.wizard", "true") }

// Should be passed manually (through TC or run configuration)
val isRadler by lazy { System.getProperty("intellij.clion.radler.perf.tests", "false").toBoolean() }
