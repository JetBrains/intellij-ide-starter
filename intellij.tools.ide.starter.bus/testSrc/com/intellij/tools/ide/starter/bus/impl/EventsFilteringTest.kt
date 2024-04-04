package com.intellij.tools.ide.starter.bus.impl

import com.intellij.tools.ide.starter.bus.StarterBus
import com.intellij.tools.ide.starter.bus.events.Event
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal fun checkIsEventProcessed(shouldEventBeProcessed: Boolean, isEventProcessedGetter: () -> Boolean) {
  runBlocking {
    withTimeout(2.seconds) {
      if (isEventProcessedGetter() == shouldEventBeProcessed) return@withTimeout
      delay(50.milliseconds)
    }
  }
}

class EventsFilteringTest {
  private var isEventProcessed: AtomicBoolean = AtomicBoolean(false)

  @BeforeEach
  fun beforeEach() {
    isEventProcessed.set(false)
  }

  @AfterEach
  fun afterEach() {
    StarterBus.unsubscribeAll()
  }

  class CustomEvent : Event()
  class BeforeEvent : Event()
  class AfterEvent : Event()
  class AnotherCustomEvent : Event()

  @RepeatedTest(value = 200)
  fun `filtering events by type is working`() {
    StarterBus.subscribe(this) { _: Event ->
      isEventProcessed.set(true)
    }

    StarterBus.postAndWaitProcessing(CustomEvent())
    checkIsEventProcessed(false) { isEventProcessed.get() }

    StarterBus.postAndWaitProcessing(Event())
    checkIsEventProcessed(true) { isEventProcessed.get() }
  }

  @RepeatedTest(value = 100)
  fun `single event is published`() {
    StarterBus.subscribe(this) { _: Event ->
      isEventProcessed.set(true)
    }

    StarterBus.postAndWaitProcessing(Event())
    checkIsEventProcessed(true) { isEventProcessed.get() }
  }

  @RepeatedTest(value = 100)
  fun `multiple same events is published and handled by subscribers`() {
    val firstSubscriberInvocationsData = mutableSetOf<Any>()
    val secondSubscriberInvocationsData = mutableSetOf<Any>()

    StarterBus
      .subscribe(this) { event: Event -> firstSubscriberInvocationsData.add(event) }
      .subscribe(this) { event: Event -> secondSubscriberInvocationsData.add(event) }

    val firstSignal = BeforeEvent()
    StarterBus.postAndWaitProcessing(firstSignal)
    StarterBus.postAndWaitProcessing(CustomEvent())
    val secondSignal = AfterEvent()
    StarterBus.postAndWaitProcessing(secondSignal)
    StarterBus.postAndWaitProcessing(AnotherCustomEvent())

    firstSubscriberInvocationsData.containsAll(listOf(firstSignal, secondSignal))
    secondSubscriberInvocationsData.containsAll(listOf(firstSignal, secondSignal))
  }

  @Test
  fun `filtering custom events in subscribe`() {
    StarterBus.subscribe(this) { _: CustomEvent ->
      isEventProcessed.set(true)
    }

    repeat(5) {
      StarterBus.postAndWaitProcessing(BeforeEvent())
      StarterBus.postAndWaitProcessing(AfterEvent())
      checkIsEventProcessed(shouldEventBeProcessed = false) { isEventProcessed.get() }
    }

    StarterBus.postAndWaitProcessing(CustomEvent())
    checkIsEventProcessed(shouldEventBeProcessed = true) { isEventProcessed.get() }
  }
}