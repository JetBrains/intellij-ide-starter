package com.intellij.ide.starter.plugins

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.tools.ide.util.common.logOutput
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.*

class PluginNotFoundException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause)

open class PluginConfigurator(val testContext: IDETestContext) {
  val disabledPluginsPath: Path
    get() = testContext.paths.configDir / "disabled_plugins.txt"

  fun installPluginFromPath(pathToPluginArchive: Path): PluginConfigurator = apply {
    FileSystem.unpack(pathToPluginArchive, testContext.paths.pluginsDir)
  }

  fun installPluginFromURL(urlToPluginZipFile: String): PluginConfigurator = apply {
    val pluginRootDir = GlobalPaths.instance.getCacheDirectoryFor("plugins")
    val pluginZip: Path = pluginRootDir / testContext.ide.build / urlToPluginZipFile.substringAfterLast("/")
    logOutput("Downloading $urlToPluginZipFile")

    try {
      HttpClient.download(urlToPluginZipFile, pluginZip)
    }
    catch (t: HttpClient.HttpNotFound) {
      throw PluginNotFoundException("Plugin $urlToPluginZipFile couldn't be downloaded: ${t.message}", t)
    }

    FileSystem.unpack(pluginZip, testContext.paths.pluginsDir)
  }

  fun installPluginFromPluginManager(
    pluginId: String,
    ide: InstalledIde,
    channel: String? = null,
    pluginFileName: String? = null,
  ): PluginConfigurator = installPluginFromPluginManager(PluginLatestForIde(pluginId, ide, channel, pluginFileName))

  fun installPluginFromPluginManager(
    pluginId: String,
    pluginVersion: String,
    channel: String? = null,
    pluginFileName: String? = null,
  ): PluginConfigurator = installPluginFromPluginManager(PluginWithExactVersion(pluginId, pluginVersion, channel, pluginFileName))

  fun installPluginFromPluginManager(
    plugin: PluginSourceDescriptor,
  ): PluginConfigurator = apply {
    val pluginId = plugin.pluginId
    logOutput("Setting up plugin: $pluginId ...")

    val pluginsCacheDor = GlobalPaths.instance.getCacheDirectoryFor("plugins")
    val fileName = plugin.pluginFileName ?: (pluginId.replace(".", "-") + ".zip")

    val downloadedPlugin: Path = when (plugin) {
      is PluginLatestForIde ->
        (pluginsCacheDor / plugin.ide.build).createDirectories() / fileName
      is PluginWithExactVersion ->
        (pluginsCacheDor / plugin.version).createDirectories() / fileName
    }

    HttpClient.downloadIfMissing(plugin.downloadUrl(), downloadedPlugin, retries = 1)
    if (fileName.endsWith(".jar")) {
      Files.copy(downloadedPlugin, testContext.paths.pluginsDir.resolve(fileName))
    }
    else {
      FileSystem.unpack(downloadedPlugin, testContext.paths.pluginsDir)
    }


    logOutput("Plugin $pluginId setup finished")
  }

  fun disablePlugins(vararg pluginIds: String): PluginConfigurator = disablePlugins(pluginIds.toSet())

  fun disablePlugins(pluginIds: Set<String>): PluginConfigurator = also {
    disabledPluginsPath.writeLines(disabledPluginIds + pluginIds)
  }

  fun enablePlugins(vararg pluginIds: String): PluginConfigurator = enablePlugins(pluginIds.toSet())

  private fun enablePlugins(pluginIds: Set<String>) = also {
    disabledPluginsPath.writeLines(disabledPluginIds - pluginIds)
  }

  private val disabledPluginIds: Set<String>
    get() {
      val file = disabledPluginsPath
      return if (file.exists()) file.readLines().toSet() else emptySet()
    }


  private fun findPluginXmlByPluginIdInAGivenDir(pluginId: String, bundledPluginsDir: Path): Boolean {
    val jarFiles = bundledPluginsDir.toFile().walk().filter { it.name.endsWith(".jar") }.toList()
    jarFiles.forEach {
      val jarFile = JarFile(it)
      val entry = jarFile.getJarEntry("META-INF/plugin.xml")
      if (entry != null) {
        val inputStream = jarFile.getInputStream(entry)
        val text: String = inputStream.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
        if (text.contains(" <id>$pluginId</id>")) {
          return true
        }
      }
    }
    return false
  }


  fun getPluginInstalledState(pluginId: String): PluginInstalledState {
    if (disabledPluginsPath.exists() && pluginId in disabledPluginIds) {
      return PluginInstalledState.DISABLED
    }

    val installedPluginDir = testContext.paths.pluginsDir
    if (findPluginXmlByPluginIdInAGivenDir(pluginId, installedPluginDir)) {
      return PluginInstalledState.INSTALLED
    }

    val bundledPluginsDir = testContext.ide.bundledPluginsDir
    if (bundledPluginsDir == null) {
      logOutput("Cannot ensure a plugin '$pluginId' is installed in ${testContext.ide}. Consider it is installed.")
      return PluginInstalledState.INSTALLED
    }

    if (findPluginXmlByPluginIdInAGivenDir(pluginId, bundledPluginsDir)) {
      return PluginInstalledState.BUNDLED_TO_IDE
    }
    return PluginInstalledState.NOT_INSTALLED
  }

  fun assertPluginIsInstalled(pluginId: String): PluginConfigurator = when (getPluginInstalledState(pluginId)) {
    PluginInstalledState.DISABLED -> error("Plugin '$pluginId' must not be listed in the disabled plugins file ${disabledPluginsPath}")
    PluginInstalledState.NOT_INSTALLED -> error("Plugin '$pluginId' must be installed")
    PluginInstalledState.BUNDLED_TO_IDE -> this
    PluginInstalledState.INSTALLED -> this
  }
}
