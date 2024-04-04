package com.intellij.tools.ide.starter.bus.impl

import com.intellij.tools.ide.starter.bus.StarterBus
import com.intellij.tools.ide.starter.bus.events.Event
import kotlinx.coroutines.delay
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.RepeatedTest
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime


class WaitingForSubscribersTest {

  @AfterEach
  fun afterEach() {
    StarterBus.unsubscribeAll()
  }

  class CustomEvent : Event()

  @RepeatedTest(value = 10)
  fun `waiting till subscribers finish their work`() {
    val firstSubscriberProcessedEvent = AtomicBoolean(false)
    val secondSubscriberProcessedEvent = AtomicBoolean(false)

    val firstSubscriberDelay = 2.seconds
    val secondSubscriberDelay = 4.seconds

    StarterBus
      .subscribe("First") { _: Event ->
        delay(firstSubscriberDelay)
        firstSubscriberProcessedEvent.set(true)
      }
      .subscribe("Second") { _: Event ->
        delay(secondSubscriberDelay)
        secondSubscriberProcessedEvent.set(true)
      }

    val timeout = 6.seconds

    // First event should not be processed by subscribers. Method should complete without waiting
    val firstEventDuration = measureTime {
      StarterBus.postAndWaitProcessing(CustomEvent(), timeout = timeout)
    }
    checkIsEventProcessed(false) { firstSubscriberProcessedEvent.get() }
    checkIsEventProcessed(false) { secondSubscriberProcessedEvent.get() }
    assertTrue(firstEventDuration < 100.milliseconds)


    val secondEventDuration = measureTime {
      StarterBus.postAndWaitProcessing(Event(), timeout = timeout)
    }
    checkIsEventProcessed(true) { firstSubscriberProcessedEvent.get() }
    checkIsEventProcessed(true) { secondSubscriberProcessedEvent.get() }
    assertTrue(secondEventDuration < timeout)
    assertTrue(secondEventDuration >= secondSubscriberDelay)
  }

  @RepeatedTest(value = 5)
  fun `unsuccessful awaiting of subscribers`() {
    val firstSubscriberProcessedEvent = AtomicBoolean(false)
    val secondSubscriberProcessedEvent = AtomicBoolean(false)
    val gotException = AtomicBoolean(false)

    val firstSubscriberDelay = 4.seconds
    val secondSubscriberDelay = 6.seconds

    StarterBus
      .subscribe(firstSubscriberProcessedEvent) { _: Event ->
        delay(firstSubscriberDelay)
        firstSubscriberProcessedEvent.set(true)
      }
      .subscribe(secondSubscriberProcessedEvent) { _: Event ->
        delay(secondSubscriberDelay)
        secondSubscriberProcessedEvent.set(true)
      }

    val timeout = 2.seconds
    val duration = measureTime {
      try {
        StarterBus.postAndWaitProcessing(Event(), timeout = timeout)
      }
      catch (t: TimeoutException) {
        gotException.set(true)
      }
    }

    assertFalse(firstSubscriberProcessedEvent.get())
    assertFalse(secondSubscriberProcessedEvent.get())

    assertTrue(gotException.get())
    assertTrue(duration >= timeout)
    assertTrue(duration < timeout.plus(1.seconds))
  }
}