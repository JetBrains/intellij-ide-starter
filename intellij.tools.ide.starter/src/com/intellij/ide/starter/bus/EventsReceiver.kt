package com.intellij.ide.starter.bus

import com.intellij.ide.starter.utils.catchAll
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull

/**
 * Subscriber type to List of Jobs
 */
class Subscriptions(val items: MutableMap<Any, MutableList<Job>> = mutableMapOf())

/**
 * @author https://github.com/Kosert/FlowBus
 * @license Apache 2.0 https://github.com/Kosert/FlowBus/blob/master/LICENSE
 * Class for receiving events posted to [FlowBus]
 *
 * @param bus [FlowBus] instance to subscribe to. If not set, [StarterBus] will be used
 */
open class EventsReceiver @JvmOverloads constructor(private val bus: FlowBus) {
  private val jobs = mutableMapOf<Class<*>, Subscriptions>()
  private var returnDispatcher: CoroutineDispatcher = Dispatchers.Unconfined

  /**
   * Subscribe to events that are type of [eventType] with the given [callback] function.
   * The [callback] can be called immediately if event of type [eventType] is present in the flow.
   *
   * @param eventType Type of event to subscribe to
   * @param skipRetained Skips event already present in the flow. This is `false` by default
   * @param callback The callback function
   * @return This instance of [EventsReceiver] for chaining
   */
  @JvmOverloads
  fun <EventType : Any, SubscriberType : Any> subscribeTo(eventType: Class<EventType>,
                                                          subscriber: SubscriberType,
                                                          skipRetained: Boolean = false,
                                                          subscribeOnlyOnce: Boolean = false,
                                                          eventStateFilter: (EventState) -> Boolean = { true },
                                                          callback: suspend (event: EventType) -> Unit): EventsReceiver {
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
      throw throwable
    }

    // in case if there are many subscriptions from the same subscriber class
    synchronized(subscriber) {
      val subscriberJobs: Subscriptions = jobs[eventType] ?: Subscriptions()

      // subscribe only once if subscriber type is specified
      if (subscribeOnlyOnce && subscriberJobs.items[subscriber]?.isNotEmpty() == true) return this

      val job = CoroutineScope(Job() + returnDispatcher + exceptionHandler).launch {
        bus.forEvent(eventType)
          .drop(if (skipRetained) 1 else 0)
          .filterNotNull()
          .collect { event ->
            withContext(returnDispatcher) {
              if (eventStateFilter((event as Signal).state)) {
                catchAll { callback(event) }
              }
              bus.getSynchronizer(event)?.countDown()
            }
          }
      }

      subscriberJobs.items.putIfAbsent(subscriber, mutableListOf())
      subscriberJobs.items[subscriber]!!.add(job)
      jobs.putIfAbsent(eventType, subscriberJobs)
    }

    return this
  }

  /**
   * Simplified [subscribeTo] for Kotlin.
   * Type of event is automatically inferred from [callback] parameter type.
   *
   * The subscription will be unsubscribed after the test.
   *
   * @param skipRetained Skips event already present in the flow. This is `false` by default
   * @param callback The callback function
   * @return This instance of [EventsReceiver] for chaining
   */
  inline fun <reified EventType : Any, reified SubscriberType : Any> subscribe(
    subscriber: SubscriberType,
    skipRetained: Boolean = false,
    noinline eventStateFilter: (EventState) -> Boolean = { true },
    noinline callback: suspend (event: EventType) -> Unit
  ): EventsReceiver {
    return subscribeTo<EventType, SubscriberType>(eventType = EventType::class.java, subscriber = subscriber, skipRetained = skipRetained,
                                                  subscribeOnlyOnce = false, eventStateFilter = eventStateFilter, callback)
  }

  /** Guarantees, that subscriber [SubscriberType] will be subscribed to event [EventType] only once
   * no matter how many times subscription method will be invoked */
  inline fun <reified EventType : Any, reified SubscriberType : Any> subscribeOnlyOnce(
    subscriber: SubscriberType,
    skipRetained: Boolean = false,
    noinline eventStateFilter: (EventState) -> Boolean = { true },
    noinline callback: suspend (event: EventType) -> Unit
  ): EventsReceiver {
    return subscribeTo<EventType, SubscriberType>(eventType = EventType::class.java, subscriber = subscriber, skipRetained = skipRetained,
                                                  subscribeOnlyOnce = true, eventStateFilter = eventStateFilter, callback)
  }

  /**
   * Unsubscribe from all events
   */
  @Suppress("RAW_RUN_BLOCKING")
  fun unsubscribe() {
    runBlocking(returnDispatcher) {
      for (subscriptions in jobs.values) {
        for (job in subscriptions.items.flatMap { it.value }) {
          job.cancelAndJoin()
        }
      }
    }

    jobs.clear()
  }

  internal fun <T : Any> getSubscriptions(eventType: Class<T>): Subscriptions {
    return jobs[eventType] ?: Subscriptions()
  }
}
