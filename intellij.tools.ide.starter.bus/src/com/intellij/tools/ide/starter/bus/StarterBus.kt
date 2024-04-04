package com.intellij.tools.ide.starter.bus

import com.intellij.tools.ide.starter.bus.events.Event
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Event bus */
object StarterBus {
  private val PRODUCER = EventsProducer()
  val LISTENER = EventsReceiver(PRODUCER)

  /** Post [event] and await processing.
   * Different events can be processed in parallel
   * Throws [TimeoutException] after [timeout]
   *  */
  fun <T : Event> postAndWaitProcessing(event: T, timeout: Duration = 30.seconds) {
    PRODUCER.postAndWaitProcessing(event, timeout)
  }

  /** Subscribes [subscriber] by [SubscriberType] to event [EventType]
   * Can have only one subscription by pair subscriber + event
   *  */
  inline fun <reified EventType : Event, reified SubscriberType : Any> subscribe(
    subscriber: SubscriberType,
    noinline callback: suspend (event: EventType) -> Unit
  ): StarterBus {
    LISTENER.subscribeTo(event = EventType::class.java, subscriber = subscriber, callback)
    return this
  }

  fun unsubscribeAll() {
    LISTENER.unsubscribeAll()
  }
}