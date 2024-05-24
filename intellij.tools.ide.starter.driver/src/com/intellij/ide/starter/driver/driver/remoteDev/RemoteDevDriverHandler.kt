package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.ide.starter.driver.engine.DriverHandler
import com.intellij.ide.starter.driver.engine.DriverOptions


class RemoteDevDriverOptions : DriverOptions() {
  var hostWebServerPort: Int = 63343
  var hostDriverPort: Int = 7777
  var hostDebugPort: Int = 5020
  var hostSystemProperties: Map<String, String> = systemProperties
}

class RemoteDevDriverHandler : DriverHandler() {
  companion object {
    fun rdctVmOptions(options: RemoteDevDriverOptions): Map<String, String> =
      mapOf("rdct.tests.backendJmxPort" to options.hostDriverPort.toString())
  }
}