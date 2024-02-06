package com.intellij.ide.starter.report

enum class ErrorType{
  ERROR, FREEZE;

  companion object {
    fun fromMessage(message: String): ErrorType =
      if (message.startsWith("UI was frozen") || message.startsWith("Freeze in EDT")) FREEZE else ERROR
  }
}