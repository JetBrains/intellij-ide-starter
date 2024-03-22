package com.intellij.ide.starter.runner

import com.intellij.tools.ide.starter.bus.Event
import com.intellij.tools.ide.starter.bus.EventState

data class IdeLaunchExceptionData(val exception: Throwable,
                                  val runContext: IDERunContext? = null,
                                  val ideProcess: Process? = null,
                                  val ideProcessId: Long? = null)

class IdeLaunchException(state: EventState, data: IdeLaunchExceptionData) : Event<IdeLaunchExceptionData>(state, data)