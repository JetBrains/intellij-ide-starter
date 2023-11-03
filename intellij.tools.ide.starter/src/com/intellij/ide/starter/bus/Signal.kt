package com.intellij.ide.starter.bus

/**
 * Very simple event-indicator, that something happened.
 * If you need to pass data with event @see [com.intellij.ide.starter.bus.Event]
 */
open class Signal(val state: EventState = EventState.UNDEFINED)