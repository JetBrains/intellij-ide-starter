package com.intellij.ide.starter.driver

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeoutException
import kotlin.time.Duration

@Suppress("SSBasedInspection")
fun waitForCondition(timeout: Duration, pollInterval: Duration, condition: () -> Boolean) {
  return runBlocking {
    withTimeoutOrNull(timeout) {
      while (!condition()) {
        delay(pollInterval)
      }
    }
  } ?: throw TimeoutException()
}