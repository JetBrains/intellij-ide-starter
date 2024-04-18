package com.intellij.tools.ide.starter.bus.shared

import com.intellij.tools.ide.starter.bus.EventsBus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

abstract class SharedEventsTest {

  @BeforeEach
  fun abstractBeforeEach() {
    EventsBus.startServerProcess()
  }

  @AfterEach
  fun abstractAfterEach() {
    EventsBus.unsubscribeAll()
    EventsBus.endServerProcess()
  }
}