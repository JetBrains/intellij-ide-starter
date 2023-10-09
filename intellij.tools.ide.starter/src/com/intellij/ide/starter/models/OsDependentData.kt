package com.intellij.ide.starter.models

import com.intellij.openapi.util.OsFamily
import com.intellij.openapi.util.SystemInfo

class OsDataStorage<T>(vararg val items: Pair<OsFamily, T>) {
  val get: T
    get() {
      require(items.isNotEmpty()) { "Os dependent data should not be empty when accessing it" }

      val osSpecificData = when {
        SystemInfo.isMac -> items.firstOrNull { it.first == OsFamily.MacOS }
        SystemInfo.isWindows -> items.firstOrNull { it.first == OsFamily.Windows }
        SystemInfo.isLinux -> items.firstOrNull { it.first == OsFamily.Linux }
        else -> items.first { it.first == OsFamily.Other }
      }

      osSpecificData?.let { return it.second }

      return items.first { it.first == OsFamily.Other }.second
    }
}