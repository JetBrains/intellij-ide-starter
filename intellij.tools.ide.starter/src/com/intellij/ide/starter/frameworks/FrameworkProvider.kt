package com.intellij.ide.starter.frameworks

import com.intellij.ide.starter.frameworks.android.AndroidFramework
import com.intellij.ide.starter.frameworks.android.SpringFramework
import com.intellij.ide.starter.ide.IDETestContext

abstract class FrameworkProvider(val testContext: IDETestContext) {
  abstract val android: AndroidFramework
  abstract val spring: SpringFramework
}