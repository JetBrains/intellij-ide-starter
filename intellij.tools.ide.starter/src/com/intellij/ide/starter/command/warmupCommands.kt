// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.command

import com.intellij.ide.starter.command.CommandChain
import com.intellij.openapi.ui.playback.commands.AbstractCommand

const val PREFIX = AbstractCommand.CMD_PREFIX + "checkWarmupBuild"

fun <T : CommandChain> T.checkWarmupBuild(): T {
  addCommand(PREFIX)
  return this
}