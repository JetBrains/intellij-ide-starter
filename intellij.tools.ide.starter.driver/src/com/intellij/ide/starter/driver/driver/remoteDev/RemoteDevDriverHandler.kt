package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.ide.starter.driver.engine.DriverHandler
import com.intellij.ide.starter.driver.engine.DriverOptions
import com.intellij.util.net.NetUtils


class RemoteDevDriverOptions : DriverOptions() {
  var backendWebServerPort: Int = webServerPort
  var backendDriverPort: Int = driverPort
  var frontendWebServerPort: Int = NetUtils.findAvailableSocketPort()
  var frontendDriverPort: Int = NetUtils.findAvailableSocketPort()
  var backendDebugPort: Int = 5020
  var backendSystemProperties: Map<String, String> = systemProperties
}

class RemoteDevDriverHandler : DriverHandler() {
  companion object {
    fun rdctVmOptions(options: RemoteDevDriverOptions): Map<String, String> =
      mapOf("rdct.tests.backendJmxPort" to options.backendDriverPort.toString(),
            "ide.mac.file.chooser.native" to "false",
            "apple.laf.useScreenMenuBar" to "false",
            "jbScreenMenuBar.enabled" to "false")
  }
}