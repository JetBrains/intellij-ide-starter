package com.intellij.ide.starter.extended.statistics

import com.intellij.internal.statistic.eventLog.LogEventSerializer
import com.jetbrains.fus.reporting.model.lion3.LogEvent

fun LogEvent.print(): String {
  return LogEventSerializer.toString(this)
}