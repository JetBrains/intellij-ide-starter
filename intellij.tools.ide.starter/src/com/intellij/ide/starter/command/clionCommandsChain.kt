package com.intellij.ide.starter.command

import com.intellij.ide.starter.command.CommandChain

// prototype for test-local language-plugin agnostic commands
fun <T : CommandChain> T.clionDummy(): T {
  addCommand("%CLionDummyCommand")
  return this
}

// Using `waitForSymbols` command from OCWaitForSymbolsCommand as a string to avoid unnecessary dependencies
// The implementation is different for CLion and Radler
fun <T : CommandChain> T.clionWaitForSymbols(): T {
  addCommand("%waitForSymbols")
  return this
}