package com.intellij.ide.starter.bus

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Event bus */
object StarterBus {
  val BUS = FlowBus()
  val LISTENER = EventsReceiver(BUS)

  /** @see [com.intellij.ide.starter.bus.FlowBus.postAsync(T, boolean)] */
  fun <T : Any> postAsync(event: T, retain: Boolean = true) {
    BUS.postAsync(event, retain)
  }

  /** @see [com.intellij.ide.starter.bus.FlowBus.postAndWaitProcessing] */
  fun <T : Any> postAndWaitProcessing(event: T, retain: Boolean = true, timeout: Duration = 30.seconds): Boolean {
    return BUS.postAndWaitProcessing(event, LISTENER, retain, timeout = timeout)
  }

  /** @see [com.intellij.ide.starter.bus.EventsReceiver.subscribe] */
  inline fun <reified EventType : Any, reified SubscriberType : Any> subscribe(
    subscriber: SubscriberType,
    skipRetained: Boolean = false,
    noinline callback: suspend (event: EventType) -> Unit
  ): StarterBus {
    LISTENER.subscribe<EventType, SubscriberType>(subscriber, skipRetained, callback)
    return this
  }

  /** @see [com.intellij.ide.starter.bus.EventsReceiver.subscribeOnlyOnce] */
  inline fun <reified EventType : Any, reified SubscriberType : Any> subscribeOnlyOnce(
    subscriber: SubscriberType,
    skipRetained: Boolean = false,
    noinline callback: suspend (event: EventType) -> Unit
  ): StarterBus {
    LISTENER.subscribeOnlyOnce<EventType, SubscriberType>(subscriber, skipRetained, callback)
    return this
  }
}