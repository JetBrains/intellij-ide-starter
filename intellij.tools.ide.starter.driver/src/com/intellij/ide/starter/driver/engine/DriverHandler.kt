package com.intellij.ide.starter.driver.engine

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.to

open class DriverOptions {
  var systemProperties: Map<String, String> = mapOf()
  var runTimeout: Duration = 10.minutes
  var driverPort: Int = 8889
  var webServerPort: Int = 7778
  var debugPort: Int = 5010
}

open class DriverHandler {
  companion object {
    fun systemProperties(host: String = "127.0.0.1", port: Int): Map<String, String> {
      // https://docs.oracle.com/javase/8/docs/technotes/guides/serialization/filters/serialization-filtering.html
      return mapOf(
        "java.rmi.server.hostname" to host,
        "com.sun.management.jmxremote" to "true",
        "com.sun.management.jmxremote.port" to port.toString(),
        "com.sun.management.jmxremote.authenticate" to "false",
        "com.sun.management.jmxremote.ssl" to "false",
        "com.sun.management.jmxremote.serial.filter.pattern" to "'java.**;javax.**;com.intellij.driver.model.**'",
        "expose.ui.hierarchy.url" to "true",
      )
    }
  }
}