package com.intellij.ide.starter.junit5.events

import com.intellij.ide.starter.bus.Signal
import com.intellij.ide.starter.bus.StarterBus
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import kotlinx.coroutines.delay
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.RepeatedTest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class WaitingForSubscribersTest {
  @AfterEach
  fun afterEach() {
    StarterBus.LISTENER.unsubscribe()
  }

  class CustomSignal : Signal()

  @RepeatedTest(value = 5)
  fun `waiting till subscribers finish their work`() {
    val firstSubscriberProcessedEvent = AtomicBoolean(false)
    val secondSubscriberProcessedEvent = AtomicBoolean(false)

    val firstSubscriberDelay = 2.seconds
    val secondSubscriberDelay = 4.seconds

    StarterBus
      .subscribe(this) { _: Signal ->
        delay(firstSubscriberDelay)
        firstSubscriberProcessedEvent.set(true)
      }
      .subscribe(this) { _: Signal ->
        delay(secondSubscriberDelay)
        secondSubscriberProcessedEvent.set(true)
      }

    val timeout = 6.seconds

    // First event should not be processed by subscribers. Method should complete without waiting
    val firstEventDuration = measureTime {
      StarterBus.postAndWaitProcessing(CustomSignal(), timeout = timeout).shouldBeTrue()
    }
    checkIsEventProcessed(false) { firstSubscriberProcessedEvent.get() }
    checkIsEventProcessed(false) { secondSubscriberProcessedEvent.get() }
    withClue("Event without subscribers should be processed immediately") {
      firstEventDuration.shouldBeLessThan(100.milliseconds)
    }


    val secondEventDuration = measureTime {
      StarterBus.postAndWaitProcessing(Signal(), timeout = timeout).shouldBeTrue()
    }
    checkIsEventProcessed(true) { firstSubscriberProcessedEvent.get() }
    checkIsEventProcessed(true) { secondSubscriberProcessedEvent.get() }
    secondEventDuration.shouldBeLessThan(timeout)
    secondEventDuration.shouldBeGreaterThanOrEqualTo(secondSubscriberDelay.coerceAtLeast(firstSubscriberDelay))
  }

  @RepeatedTest(value = 5)
  fun `unsuccessful awaiting of subscribers`() {
    val firstSubscriberProcessedEvent = AtomicBoolean(false)
    val secondSubscriberProcessedEvent = AtomicBoolean(false)

    val firstSubscriberDelay = 4.seconds
    val secondSubscriberDelay = 6.seconds

    StarterBus
      .subscribe(this) { _: Signal ->
        delay(firstSubscriberDelay)
        firstSubscriberProcessedEvent.set(true)
      }
      .subscribe(this) { _: Signal ->
        delay(secondSubscriberDelay)
        secondSubscriberProcessedEvent.set(true)
      }

    val timeout = 2.seconds
    val duration = measureTime {
      StarterBus.postAndWaitProcessing(Signal(), timeout = timeout).shouldBeFalse()
    }

    withClue("Event will should not be immediately processed by subscribers (since they are long running tasks)") {
      firstSubscriberProcessedEvent.get().shouldBeFalse()
      secondSubscriberProcessedEvent.get().shouldBeFalse()
    }

    duration.shouldBeGreaterThanOrEqualTo(timeout)
    duration.shouldBeLessThan(timeout.plus(1.seconds))
  }
}