package com.intellij.ide.starter.bus

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Event bus */
object StarterBus {
  val BUS = FlowBus()
  val LISTENER = EventsReceiver(BUS)

  /** @see com.intellij.ide.starter.bus.FlowBus.postAndWaitProcessing */
  fun <T : Signal> postAndWaitProcessing(event: T, retain: Boolean = true, timeout: Duration = 30.seconds): Boolean {
    return BUS.postAndWaitProcessing(event, LISTENER, retain, timeout = timeout)
  }

  /** @see com.intellij.ide.starter.bus.EventsReceiver.subscribe */
  inline fun <reified EventType : Signal, reified SubscriberType : Any> subscribe(
    subscriber: SubscriberType,
    skipRetained: Boolean = false,
    eventState: EventState,
    noinline callback: suspend (event: EventType) -> Unit
  ): StarterBus {
    LISTENER.subscribe<EventType, SubscriberType>(subscriber = subscriber, skipRetained = skipRetained,
                                                  eventStateFilter = { it == eventState }, callback)
    return this
  }

  /** @see com.intellij.ide.starter.bus.EventsReceiver.subscribe */
  inline fun <reified EventType : Signal, reified SubscriberType : Any> subscribe(
    subscriber: SubscriberType,
    skipRetained: Boolean = false,
    noinline eventStateFilter: (EventState) -> Boolean = { true },
    noinline callback: suspend (event: EventType) -> Unit
  ): StarterBus {
    LISTENER.subscribe<EventType, SubscriberType>(subscriber = subscriber, skipRetained = skipRetained,
                                                  eventStateFilter = eventStateFilter, callback)
    return this
  }

  /** @see com.intellij.ide.starter.bus.EventsReceiver.subscribeOnlyOnce */
  inline fun <reified EventType : Signal, reified SubscriberType : Any> subscribeOnlyOnce(
    subscriber: SubscriberType,
    skipRetained: Boolean = false,
    eventState: EventState,
    noinline callback: suspend (event: EventType) -> Unit
  ): StarterBus {
    LISTENER.subscribeOnlyOnce<EventType, SubscriberType>(subscriber = subscriber, skipRetained = skipRetained,
                                                          eventStateFilter = { it == eventState }, callback)
    return this
  }

  /** @see com.intellij.ide.starter.bus.EventsReceiver.subscribeOnlyOnce */
  inline fun <reified EventType : Signal, reified SubscriberType : Any> subscribeOnlyOnce(
    subscriber: SubscriberType,
    skipRetained: Boolean = false,
    noinline eventStateFilter: (EventState) -> Boolean = { true },
    noinline callback: suspend (event: EventType) -> Unit
  ): StarterBus {
    LISTENER.subscribeOnlyOnce<EventType, SubscriberType>(subscriber = subscriber, skipRetained = skipRetained,
                                                          eventStateFilter = eventStateFilter, callback)
    return this
  }
}