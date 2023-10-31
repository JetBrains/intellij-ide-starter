package com.intellij.ide.starter.junit5

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

internal fun checkIsEventFired(shouldEventBeFired: Boolean, isEventFiredGetter: () -> Boolean) {
  val shouldNotMessage = if (!shouldEventBeFired) "NOT" else ""

  runBlocking {
    eventually(duration = 2.seconds, poll = 50.milliseconds) {
      withClue("Event should $shouldNotMessage be fired in 2 sec") {
        isEventFiredGetter().shouldBe(shouldEventBeFired)
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

  @RepeatedTest(value = 200)
  fun `filtering events by type is working`() {
    StarterBus.subscribe<Signal> {
      isEventProcessed.set(true)
    }

    StarterBus.postAsync(2)
    checkIsEventFired(false) { isEventProcessed.get() }

    StarterBus.postAsync(Signal())
    checkIsEventFired(true) { isEventProcessed.get() }
  }

  @RepeatedTest(value = 100)
  fun `single event is published`() {
    StarterBus.subscribe<Signal> {
      isEventProcessed.set(true)
    }

    StarterBus.postAsync(Signal())
    checkIsEventFired(true) { isEventProcessed.get() }
  }

  @RepeatedTest(value = 100)
  fun `multiple same events is published and handled by subscribers`() {
    val firstSubscriberInvocationsData = mutableSetOf<Any>()
    val secondSubscriberInvocationsData = mutableSetOf<Any>()

    StarterBus
      .subscribe<Signal> { firstSubscriberInvocationsData.add(it) }
      .subscribe<Signal> { secondSubscriberInvocationsData.add(it) }

    val firstSignal = Signal(EventState.BEFORE)
    StarterBus.postAsync(firstSignal)
    StarterBus.postAsync(Any())
    val secondSignal = Signal(EventState.AFTER)
    StarterBus.postAsync(secondSignal)
    StarterBus.postAsync(42)

    firstSubscriberInvocationsData.shouldContainExactly(firstSignal, secondSignal)
    secondSubscriberInvocationsData.shouldContainExactly(firstSignal, secondSignal)
  }
}