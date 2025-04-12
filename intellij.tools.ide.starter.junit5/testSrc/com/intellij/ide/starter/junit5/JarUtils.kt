package com.intellij.ide.starter.junit5

import java.net.URI
import java.net.URL
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Files.*
import java.nio.file.Path
import java.nio.file.Path.of
import java.nio.file.StandardCopyOption

object JarUtils {

  fun extractResource(resourceName: String, tempDir: Path): Path {
    val targetDir = tempDir.resolve(resourceName)
    val resourceUrl = JarUtils::class.java.classLoader.getResource(resourceName)
                      ?: throw IllegalStateException("Resource not found: $resourceName")
    createDirectories(targetDir)
    when (resourceUrl.protocol) {
      "jar" -> {
        extractFromJar(resourceUrl, resourceName, targetDir)
      }
      "file" -> {
        val resourceDir = of(resourceUrl.toURI())
        if (isDirectory(resourceDir)) {
          walk(resourceDir)
            .filter { isRegularFile(it) }
            .forEach { filePath ->
              val relativePath = resourceDir.relativize(filePath)
              val targetFile = targetDir.resolve(relativePath)
              createDirectories(targetFile.parent)
              copy(filePath, targetFile, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        else {
          throw IllegalStateException("Resource is not a directory: $resourceName")
        }
      }
      else -> {
        throw IllegalStateException("Unsupported protocol: ${resourceUrl.protocol}")
      }
    }
    return targetDir
  }

  private fun extractFromJar(resourceUrl: URL, resourcePath: String, targetDir: Path) {
    val jarUri = URI.create("jar:${resourceUrl.toURI()}")

    FileSystems.newFileSystem(jarUri, mapOf<String, Any>()).use { fs ->
      val basePath = fs.getPath(resourcePath)

      Files.walk(basePath)
        .filter { Files.isRegularFile(it) }
        .forEach { filePath ->
          val relativePath = basePath.relativize(filePath)
          val targetFile = targetDir.resolve(relativePath)
          Files.createDirectories(targetFile.parent)
          Files.copy(filePath, targetFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }
  }
}