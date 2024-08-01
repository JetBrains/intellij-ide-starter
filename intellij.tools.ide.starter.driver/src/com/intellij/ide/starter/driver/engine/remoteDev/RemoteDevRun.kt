package com.intellij.ide.starter.driver.engine.remoteDev

import com.intellij.ide.starter.di.DISnapshot
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.driver.engine.DriverRunner
import com.intellij.ide.starter.driver.driver.remoteDev.RemDevDriverRunner
import com.intellij.ide.starter.driver.driver.remoteDev.RemdevIDETestContextFactoryImpl
import com.intellij.ide.starter.ide.IDETestContextFactory
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import kotlin.text.toBoolean

open class RemoteDevRun : BeforeAllCallback, AfterAllCallback {
  companion object {
    val remoteDevRunFromEnv = System.getenv().getOrDefault("REMOTE_DEV_RUN", "false").toBoolean()
  }



  override fun beforeAll(context: ExtensionContext?) {
    di = DI {
      extend(di)
      bindSingleton<DriverRunner>(overrides = true) { RemDevDriverRunner() }
      bindSingleton<IDETestContextFactory>(overrides = true) { RemdevIDETestContextFactoryImpl() }
    }
    DISnapshot.initSnapshot(di)
  }

  override fun afterAll(context: ExtensionContext?) {
    di = DISnapshot.get()
  }
}

