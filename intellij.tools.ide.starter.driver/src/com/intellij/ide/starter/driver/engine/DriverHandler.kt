package com.intellij.ide.starter.driver.engine

import java.net.InetAddress
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
    fun systemProperties(host: InetAddress = InetAddress.getLoopbackAddress(), port: Int): Map<String, String> {
      // https://docs.oracle.com/javase/8/docs/technotes/guides/serialization/filters/serialization-filtering.html
      return mapOf(
        // the host name string that should be associated with remote stubs for locally created remote objects, in order to allow clients to invoke methods on the remote object,
        // if not specified: all interfaces the local host (127.0.0.1)
        "java.rmi.server.hostname" to host.hostAddress,
        // the bind address for the default JMX agent,
        // if not specified: all interfaces (0.0.0.0)
        "com.sun.management.jmxremote.host" to host.hostAddress,
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