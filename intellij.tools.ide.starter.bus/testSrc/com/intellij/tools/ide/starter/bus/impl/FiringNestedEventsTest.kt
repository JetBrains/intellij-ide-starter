package com.intellij.tools.ide.starter.bus.impl

import com.intellij.tools.ide.starter.bus.StarterBus
import com.intellij.tools.ide.starter.bus.events.Event
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

class FiringNestedEventsTest {
  @AfterEach
  fun afterEach() {
    StarterBus.unsubscribeAll()
  }

  class FirstEvent : Event() {
    init {
      StarterBus.postAndWaitProcessing(SecondEvent(), timeout = 2.seconds)
    }
  }

  class SecondEvent : Event()

  @Test
  fun `firing nested events should work`() {
    val firstSubscriberProcessedEvent = AtomicBoolean(false)
    val secondSubscriberProcessedEvent = AtomicBoolean(false)

    StarterBus
      .subscribe(this) { _: FirstEvent ->
        firstSubscriberProcessedEvent.set(true)
      }
      .subscribe(this) { _: SecondEvent ->
        secondSubscriberProcessedEvent.set(true)
      }

    StarterBus.postAndWaitProcessing(FirstEvent(), timeout = 2.seconds)

    assertTrue(firstSubscriberProcessedEvent.get())
    assertTrue(secondSubscriberProcessedEvent.get())
  }
}