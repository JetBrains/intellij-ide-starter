package com.intellij.ide.starter.frameworks

import com.intellij.ide.starter.frameworks.android.AndroidFramework
import com.intellij.ide.starter.frameworks.android.SpringFramework
import com.intellij.ide.starter.ide.IDETestContext

open class FrameworkDefaultProvider(testContext: IDETestContext) : FrameworkProvider(testContext) {
  override val android: AndroidFramework = AndroidFramework(testContext)
  override val spring: SpringFramework = SpringFramework(testContext)
}