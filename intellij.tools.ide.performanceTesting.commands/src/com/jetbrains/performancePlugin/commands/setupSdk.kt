package com.jetbrains.performancePlugin.commands

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.command.CommandChain
import com.intellij.ide.starter.sdk.SdkObject
import com.jetbrains.performancePlugin.commands.chain.exitApp
import com.jetbrains.performancePlugin.commands.chain.setupProjectSdk
import kotlin.time.Duration.Companion.minutes

fun IDETestContext.setupSdk(sdkObjects: SdkObject?, cleanDirs: Boolean = true): IDETestContext {
  if (sdkObjects == null) return this

  disableAutoImport(true)
    .executeRightAfterIdeOpened(true)
    .runIDE(
      commands =  CommandChain().setupProjectSdk(sdkObjects).exitApp(),
      launchName = "setupSdk",
      runTimeout = 3.minutes
    )

  if (cleanDirs)
    this
      //some caches from IDE warmup may stay
      .wipeSystemDir()
      //some logs and perf snapshots may stay
      .wipeLogsDir()


  return this
    // rollback changes, that were made only to setup sdk
    .disableAutoImport(false)
    .executeRightAfterIdeOpened(false)
}