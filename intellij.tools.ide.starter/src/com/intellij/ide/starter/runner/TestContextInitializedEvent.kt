package com.intellij.ide.starter.runner

import com.intellij.tools.ide.starter.bus.Event
import com.intellij.tools.ide.starter.bus.EventState
import com.intellij.ide.starter.ide.IDETestContext

class TestContextInitializedEvent(state: EventState, testContext: IDETestContext) : Event<IDETestContext>(state, testContext)