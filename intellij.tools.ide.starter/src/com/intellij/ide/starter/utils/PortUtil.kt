package com.intellij.ide.starter.utils

import com.intellij.ide.starter.ci.CIServer
import java.net.InetAddress
import java.net.ServerSocket

object PortUtil {

  fun isPortAvailable(host: InetAddress, port: Int): Boolean {
    return try {
      ServerSocket(port, 0, host).use { /* bound successfully */ }
      true
    }
    catch (_: Exception) {
      false
    }
  }

  fun getAvailablePort(host: InetAddress = InetAddress.getLoopbackAddress(), proposedPort: Int): Int {
    if (isPortAvailable(host, proposedPort)) {
      return proposedPort
    }
    else {
      CIServer.instance.reportTestFailure("Proposed port is not available.",
                                                    "Proposed port $proposedPort is not available on host $host.\n" +
                                                    "Busy port could mean that the previous process is still running or the port is blocked by another application.\n" +
                                                    "Please make sure to investigate, the uninvestigated hanging processes could lead to further unclear test failure.\n" +
                                                    "PLEASE BE CAREFUL WHEN MUTING",
                                          "")
      repeat(100) {
        if (isPortAvailable(host, proposedPort + it)) {
          return proposedPort + it
        }
      }
      error("No available port found")
    }
  }
}