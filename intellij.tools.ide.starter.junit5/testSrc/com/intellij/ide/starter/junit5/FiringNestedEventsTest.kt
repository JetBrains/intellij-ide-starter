package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.bus.Signal
import com.intellij.ide.starter.bus.StarterBus
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

class FiringNestedEventsTest {
  @AfterEach
  fun afterEach() {
    StarterBus.LISTENER.unsubscribe()
  }

  class FirstEvent : Signal() {
    init {
      StarterBus.postAndWaitProcessing(SecondEvent(), timeout = 2.seconds).shouldBeTrue()
    }
  }

  class SecondEvent : Signal()

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

    StarterBus.postAndWaitProcessing(FirstEvent(), timeout = 2.seconds).shouldBeTrue()

    withClue("Processing nested events should not lead to deadlock") {
      firstSubscriberProcessedEvent.get().shouldBeTrue()
      secondSubscriberProcessedEvent.get().shouldBeTrue()
    }
  }
}