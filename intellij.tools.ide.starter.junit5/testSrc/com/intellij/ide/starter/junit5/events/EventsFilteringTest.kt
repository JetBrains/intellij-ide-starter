package com.intellij.ide.starter.junit5.events

import com.intellij.ide.starter.bus.EventState
import com.intellij.ide.starter.bus.Signal
import com.intellij.ide.starter.bus.StarterBus
import io.kotest.assertions.timing.eventually
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal fun checkIsEventProcessed(shouldEventBeProcessed: Boolean, isEventProcessedGetter: () -> Boolean) {
  val shouldNotMessage = if (!shouldEventBeProcessed) "NOT" else ""

  runBlocking {
    eventually(duration = 2.seconds, poll = 50.milliseconds) {
      withClue("Event should $shouldNotMessage be fired in 2 sec") {
        isEventProcessedGetter().shouldBe(shouldEventBeProcessed)
      }
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
    StarterBus.LISTENER.unsubscribe()
  }

  class CustomSignal : Signal()
  class AnotherCustomSignal : Signal()

  @RepeatedTest(value = 200)
  fun `filtering events by type is working`() {
    StarterBus.subscribe(this) { _: Signal ->
      isEventProcessed.set(true)
    }

    StarterBus.postAsync(CustomSignal())
    checkIsEventProcessed(false) { isEventProcessed.get() }

    StarterBus.postAsync(Signal())
    checkIsEventProcessed(true) { isEventProcessed.get() }
  }

  @RepeatedTest(value = 100)
  fun `single event is published`() {
    StarterBus.subscribe(this) { _: Signal ->
      isEventProcessed.set(true)
    }

    StarterBus.postAsync(Signal())
    checkIsEventProcessed(true) { isEventProcessed.get() }
  }

  @RepeatedTest(value = 100)
  fun `multiple same events is published and handled by subscribers`() {
    val firstSubscriberInvocationsData = mutableSetOf<Any>()
    val secondSubscriberInvocationsData = mutableSetOf<Any>()

    StarterBus
      .subscribe(this) { event: Signal -> firstSubscriberInvocationsData.add(event) }
      .subscribe(this) { event: Signal -> secondSubscriberInvocationsData.add(event) }

    val firstSignal = Signal(EventState.BEFORE)
    StarterBus.postAsync(firstSignal)
    StarterBus.postAsync(CustomSignal())
    val secondSignal = Signal(EventState.AFTER)
    StarterBus.postAsync(secondSignal)
    StarterBus.postAsync(AnotherCustomSignal())

    firstSubscriberInvocationsData.shouldContainExactly(firstSignal, secondSignal)
    secondSubscriberInvocationsData.shouldContainExactly(firstSignal, secondSignal)
  }
}