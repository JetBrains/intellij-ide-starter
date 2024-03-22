package com.intellij.tools.ide.starter.bus

/**
 * Event, that holds data. If you don't need to pass data with event, take a look at [com.intellij.tools.ide.starter.bus.Signal]
 */
open class Event<T>(
  state: EventState = EventState.UNDEFINED,
  val data: T
) : Signal(state)