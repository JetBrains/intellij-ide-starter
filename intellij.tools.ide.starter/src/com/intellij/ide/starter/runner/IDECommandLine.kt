package com.intellij.ide.starter.runner

import com.intellij.ide.starter.ide.IDETestContext

sealed class IDECommandLine(open val args: List<String>) {
  data class Args(override val args: List<String>) : IDECommandLine(args) {
    constructor(vararg args: String) : this(args.toList())

    operator fun plus(params: List<String>) = copy(args = this.args + params)
  }

  object StartIdeWithoutProject : IDECommandLine(listOf())

  data class OpenTestCaseProject(val testContext: IDETestContext) : IDECommandLine(
    listOf(testContext.resolvedProjectHome.toAbsolutePath().toString())
  )
}