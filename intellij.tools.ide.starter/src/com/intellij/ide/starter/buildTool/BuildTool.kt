package com.intellij.ide.starter.buildTool

import com.intellij.ide.starter.ide.IDETestContext

/**
 * Stuff related to particular build tool
 */
open class BuildTool(val type: BuildToolType, val testContext: IDETestContext)