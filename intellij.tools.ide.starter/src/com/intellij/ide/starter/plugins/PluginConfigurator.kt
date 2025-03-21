package com.intellij.ide.starter.plugins

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.tools.ide.util.common.logOutput
import org.apache.commons.io.FileUtils
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.*

class PluginNotFoundException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause)

open class PluginConfigurator(val testContext: IDETestContext) {
  val disabledPluginsPath: Path
    get() = testContext.paths.configDir / "disabled_plugins.txt"

  fun installPluginFromPath(pathToPluginArchive: Path) = apply {
    FileSystem.unpack(pathToPluginArchive, testContext.paths.pluginsDir)
  }

  /**
   * @param pathToPluginFolder example: ~/Desktop/dev/scala-plugin-ultimate/target/Scala
   */
  fun installPluginFromFolder(pathToPluginFolder: File) = apply {
    val targetPluginsDir = testContext.paths.pluginsDir.toFile()
    val targetPluginDir = targetPluginsDir.resolve(pathToPluginFolder.name)
    logOutput("Copy plugin folder from $pathToPluginFolder to $targetPluginDir")

    if (targetPluginDir.exists()) {
      logOutput("Deleting old plugin folder from previous rungs: $targetPluginDir")
      targetPluginDir.deleteRecursively()
    }
    targetPluginDir.mkdirs()
    FileUtils.copyDirectory(pathToPluginFolder, targetPluginDir, false)
  }

  fun installPluginFromURL(urlToPluginZipFile: String) = apply {
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
  ) = installPluginFromPluginManager(PluginLatestForIde(pluginId, ide, channel, pluginFileName = pluginFileName))

  fun installPluginFromPluginManager(
    pluginId: String,
    pluginVersion: String,
    channel: String? = null,
    pluginFileName: String? = null,
  ) = installPluginFromPluginManager(PluginWithExactVersion(pluginId, pluginVersion, channel, pluginFileName = pluginFileName))

  fun installPluginFromPluginManager(
    plugin: PluginSourceDescriptor
  ) = apply {
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
    } else {
      FileSystem.unpack(downloadedPlugin, testContext.paths.pluginsDir)
    }


    logOutput("Plugin $pluginId setup finished")
  }

  fun disablePlugins(vararg pluginIds: String) = disablePlugins(pluginIds.toSet())

  fun disablePlugins(pluginIds: Set<String>) = also {
    disabledPluginsPath.writeLines(disabledPluginIds + pluginIds)
  }

  fun enablePlugins(vararg pluginIds: String) = enablePlugins(pluginIds.toSet())

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
    if (disabledPluginsPath.toFile().exists() && pluginId in disabledPluginIds) {
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