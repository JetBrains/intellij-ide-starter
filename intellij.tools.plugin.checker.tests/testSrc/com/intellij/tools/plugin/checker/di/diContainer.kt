package com.intellij.tools.plugin.checker.di

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.teamcity.TeamCityCIServer
import com.intellij.ide.starter.community.IdeByLinkDownloader
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IdeDownloader
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.IdeProduct
import com.intellij.ide.starter.models.IdeProductImp
import com.intellij.ide.starter.utils.logOutput
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.Path

internal val teamCityIntelliJPerformanceServer = TeamCityCIServer(
  fallbackServerUri = URI("https://intellij-plugins-performance.teamcity.com").normalize())
private val _isDiInitialized: AtomicBoolean = AtomicBoolean(false)

fun initPluginCheckerDI(systemPropertiesFilePath: Path = Path(System.getenv("TEAMCITY_BUILD_PROPERTIES_FILE"))) {
  synchronized(_isDiInitialized) {
    if (!_isDiInitialized.get()) {
      _isDiInitialized.set(true)

      di = DI {
        extend(di)

        bindSingleton<URI>(tag = "teamcity.uri", overrides = true) {
          teamCityIntelliJPerformanceServer.fallbackServerUri
        }
        bindSingleton<CIServer>(overrides = true) {
          TeamCityCIServer(fallbackServerUri = teamCityIntelliJPerformanceServer.fallbackServerUri,
                           systemPropertiesFilePath = systemPropertiesFilePath)
        }
        bindSingleton<IdeProduct>(overrides = true) { IdeProductImp }
        bindSingleton<IdeDownloader>(overrides = true) { IdeByLinkDownloader }
        bindSingleton<IdeProductProvider> { IdeProductProvider }
      }
    }

    logOutput("Plugin checker DI was initialized")
  }
}