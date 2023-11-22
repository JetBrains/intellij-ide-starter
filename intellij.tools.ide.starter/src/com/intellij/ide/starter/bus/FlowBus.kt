package com.intellij.ide.starter.bus

import com.intellij.tools.ide.util.common.logError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * @author https://github.com/Kosert/FlowBus
 * @license Apache 2.0 https://github.com/Kosert/FlowBus/blob/master/LICENSE
 *
 * This class holds all shared flows and handles event posting.
 * You can use [StarterBus] that is just plain instance of this class or create your own implementation.
 */
open class FlowBus {
  private val flows = ConcurrentHashMap<Class<*>, MutableSharedFlow<*>>()
  private val synchronizers = ConcurrentHashMap<Any, CountDownLatch>()

  /**
   * Gets a MutableSharedFlow for events of the given type. Creates new if one doesn't exist.
   * @return MutableSharedFlow for events that are instances of clazz
   */
  internal fun <T : Signal> forEvent(clazz: Class<T>): MutableSharedFlow<T?> {
    return flows.getOrPut(clazz) {
      MutableSharedFlow<T?>(extraBufferCapacity = 500)
    } as MutableSharedFlow<T?>
  }

  /**
   * Gets a Flow for events of the given type.
   *
   * **This flow never completes.**
   *
   * The returned Flow is _hot_ as it is based on a [SharedFlow]. This means a call to [collect] never completes normally, calling [toList] will suspend forever, etc.
   *
   * You are entirely responsible to cancel this flow. To cancel this flow, the scope in which the coroutine is running needs to be cancelled.
   * @see [SharedFlow]
   */
  fun <T : Signal> getFlow(clazz: Class<T>): Flow<T> {
    return forEvent(clazz).filterNotNull()
  }

  /**
   * @see FlowBus.getFlow
   */
  inline fun <reified T : Signal> getFlow() = getFlow(T::class.java)

  /**
   * Posts new event to SharedFlow of the [event] type.
   * Event will be processed by subscribers asynchronously.
   *
   * @param retain If the [event] should be retained in the flow for future subscribers. This is true by default.
   */
  @JvmOverloads
  fun <T : Signal> fireAndForget(event: T, retain: Boolean = true) {
    val flow = forEvent(event.javaClass)

    flow.tryEmit(event).also {
      if (!it)
        throw IllegalStateException("SharedFlow cannot take element, this should never happen")
    }
    if (!retain) {
      // without starting a coroutine here, the event is dropped immediately
      // and not delivered to subscribers
      CoroutineScope(Job() + Dispatchers.Unconfined).launch {
        dropEvent(event.javaClass)
      }
    }
  }

  /**
   * Post event and waits until all subscribers will finish their work.
   * @return True - if subscribers processed the event, false - otherwise
   */
  fun <T : Signal> postAndWaitProcessing(event: T,
                                      eventsReceiver: EventsReceiver,
                                      retain: Boolean = true,
                                      timeout: Duration = 30.seconds): Boolean {
    val subscriptions = eventsReceiver.getSubscriptions(event.javaClass)
    val subscriptionJobsSize = subscriptions.items.flatMap { it.value }.size
    val latch = CountDownLatch(subscriptionJobsSize)
    synchronizers[event] = latch
    fireAndForget(event, retain)

    val isSuccessful = latch.await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    if (!isSuccessful) {
      logError(
        """${this.javaClass.name}: ${latch.count} subscribers for event ${event.javaClass.name} haven't finished their work in $timeout.
           Complete list of subscribers: ${subscriptions.items.map { it.key.javaClass }} 
        """.trimMargin()
      )
    }

    synchronizers.remove(event)

    return isSuccessful
  }

  fun <T : Signal> getSynchronizer(event: T): CountDownLatch? {
    return synchronizers[event]
  }

  /**
   *  Removes retained event of type [clazz]
   */
  fun <T> dropEvent(clazz: Class<T>) {
    if (!flows.contains(clazz)) return
    val channel = flows[clazz] as MutableSharedFlow<T?>
    channel.tryEmit(null)
  }

  /**
   * @see FlowBus.dropEvent
   */
  inline fun <reified T : Signal> dropEvent() = dropEvent(T::class.java)

  /**
   *  Removes all retained events
   */
  fun dropAll() {
    flows.values.forEach {
      (it as MutableSharedFlow<Any?>).tryEmit(null)
    }
  }
}