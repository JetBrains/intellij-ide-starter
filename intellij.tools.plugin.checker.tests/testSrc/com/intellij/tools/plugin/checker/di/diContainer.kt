package com.intellij.tools.plugin.checker.di

import com.intellij.ide.starter.buildTool.BuildToolProvider
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.extended.build.tools.BuildToolExtendedProvider
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IdeProduct
import com.intellij.ide.starter.models.IdeProductImp
import com.intellij.ide.starter.utils.logOutput
import org.kodein.di.DI
import org.kodein.di.bindFactory
import org.kodein.di.bindSingleton
import java.util.concurrent.atomic.AtomicBoolean

private val _isDiInitialized: AtomicBoolean = AtomicBoolean(false)

fun initDI() {
  synchronized(_isDiInitialized) {
    if (!_isDiInitialized.get()) {
      _isDiInitialized.set(true)

      di = DI {
        extend(di)

        bindFactory<IDETestContext, BuildToolProvider>(overrides = true) { testContext: IDETestContext ->
          BuildToolExtendedProvider(testContext)
        }
        bindSingleton<IdeProduct>(overrides = true) { IdeProductImp }
      }
    }

    logOutput("Plugin checker DI was initialized")
  }
}