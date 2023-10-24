package com.intellij.ide.starter

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.utils.Git

fun IDETestContext.disableCMakeOpenProjectWizard() =
  applyVMOptionsPatch { this.addSystemProperty("clion.skip.open.wizard", "true") }

// Should be passed manually (through TC or run configuration)
val isRadler by lazy { System.getProperty("intellij.clion.radler.perf.tests", "false").toBoolean() }

val IdeProductProvider.Radler
  get() = CL.copy(buildType = "ijplatform_${Git.branch}_CIDR_CLion_InstallersWithRadler")
