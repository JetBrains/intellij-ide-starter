package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.bus.Event
import com.intellij.ide.starter.bus.EventState
import com.intellij.ide.starter.bus.Signal
import com.intellij.ide.starter.bus.StarterBus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

class FilteringByEventStateTest {
  private var isEventProcessed: AtomicBoolean = AtomicBoolean(false)

  @BeforeEach
  fun beforeEach() {
    isEventProcessed.set(false)
  }

  @AfterEach
  fun afterEach() {
    StarterBus.LISTENER.unsubscribe()
  }

  private class CustomEvent(state: EventState) : Event<Any>(state = state, data = Any())

  @Test
  fun `filtering signals by state in subscribe`() {
    StarterBus.subscribe(this, eventState = EventState.IN_TIME) { _: Signal ->
      isEventProcessed.set(true)
    }

    repeat(5) {
      StarterBus.postAsync(Signal(EventState.BEFORE))
      StarterBus.postAndWaitProcessing(Signal(EventState.AFTER))

      checkIsEventProcessed(shouldEventBeProcessed = false) { isEventProcessed.get() }
    }

    StarterBus.postAsync(Signal(EventState.IN_TIME))
    checkIsEventProcessed(shouldEventBeProcessed = true) { isEventProcessed.get() }
  }

  @Test
  fun `filtering custom events by state in subscribe`() {
    StarterBus.subscribe(this, eventState = EventState.IN_TIME) { _: CustomEvent ->
      isEventProcessed.set(true)
    }

    repeat(5) {
      StarterBus.postAsync(CustomEvent(EventState.BEFORE))
      StarterBus.postAndWaitProcessing(CustomEvent(EventState.AFTER))
      checkIsEventProcessed(shouldEventBeProcessed = false) { isEventProcessed.get() }
    }

    StarterBus.postAsync(CustomEvent(EventState.IN_TIME))
    checkIsEventProcessed(shouldEventBeProcessed = true) { isEventProcessed.get() }
  }

  @Test
  fun `filtering events by state in subscribeOnlyOnce`() {
    StarterBus.subscribeOnlyOnce(this, eventState = EventState.AFTER) { _: CustomEvent ->
      isEventProcessed.set(true)
    }

    repeat(5) {
      StarterBus.postAsync(CustomEvent(EventState.BEFORE))
      StarterBus.postAndWaitProcessing(CustomEvent(EventState.IN_TIME))
      checkIsEventProcessed(shouldEventBeProcessed = false) { isEventProcessed.get() }
    }

    StarterBus.postAsync(CustomEvent(EventState.AFTER))
    checkIsEventProcessed(shouldEventBeProcessed = true) { isEventProcessed.get() }
  }

  @Test
  fun `complex filtering events by state`() {
    StarterBus.subscribe(this, eventStateFilter = { it == EventState.IN_TIME || it == EventState.AFTER }) { _: CustomEvent ->
      isEventProcessed.set(true)
    }

    repeat(5) {
      StarterBus.postAsync(CustomEvent(EventState.BEFORE))
      StarterBus.postAndWaitProcessing(CustomEvent(EventState.UNDEFINED))
      StarterBus.postAsync(CustomEvent(EventState.BEFORE))

      checkIsEventProcessed(shouldEventBeProcessed = false) { isEventProcessed.get() }
    }

    StarterBus.postAsync(CustomEvent(EventState.IN_TIME))
    checkIsEventProcessed(shouldEventBeProcessed = true) { isEventProcessed.get() }

    isEventProcessed.set(false)

    StarterBus.postAsync(CustomEvent(EventState.AFTER))
    checkIsEventProcessed(shouldEventBeProcessed = true) { isEventProcessed.get() }
  }
}