package com.intellij.ide.starter.bus

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

object StarterBus {
  val BUS = FlowBus()
  val LISTENER = EventsReceiver(BUS)

  /** @see [com.intellij.ide.starter.bus.FlowBus.postAsync(T, boolean)] */
  fun <T : Any> postAsync(event: T, retain: Boolean = true) {
    BUS.postAsync(event, retain)
  }

  /** @see [com.intellij.ide.starter.bus.FlowBus.postAndWaitProcessing] */
  fun <T : Any> postAndWaitProcessing(event: T, retain: Boolean = true, timeout: Duration = 1.minutes): Boolean {
    return BUS.postAndWaitProcessing(event, LISTENER, retain, timeout = timeout)
  }

  inline fun <reified T : Any> subscribe(skipRetained: Boolean = false,
                                         noinline callback: suspend (event: T) -> Unit): StarterBus {
    LISTENER.subscribe<T>(skipRetained, callback)
    return this
  }
}