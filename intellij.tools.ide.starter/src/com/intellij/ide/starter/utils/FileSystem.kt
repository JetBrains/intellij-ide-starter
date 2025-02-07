package com.intellij.ide.starter.utils

import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.ThreeState
import com.intellij.util.io.zip.JBZipFile
import org.rauschig.jarchivelib.ArchiveFormat
import org.rauschig.jarchivelib.ArchiverFactory
import org.rauschig.jarchivelib.CompressionType
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.FileStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.zip.GZIPOutputStream
import kotlin.io.path.*
import kotlin.time.Duration.Companion.minutes

object FileSystem {
  fun String.cleanPathFromSlashes(replaceWith: String = ""): String = this
    .replace("\"", replaceWith)
    .replace("/", replaceWith)

  fun validatePath(path: Path, additionalString: String = "") {
    if (SystemInfo.isWindows) {
      val pathToValidate = when (additionalString.isNotEmpty()) {
        true -> path.resolve(additionalString).toString()
        false -> path.toString()
      }
      check(pathToValidate.length < 260) {
        "$pathToValidate >= 260 symbols on Windows may lead to unexpected problems"
      }
    }
  }

  fun countFiles(path: Path) = Files.walk(path).use { it.count() }

  fun compressToZip(sourceToCompress: Path, outputArchive: Path) {
    if (sourceToCompress.extension == "zip") {
      logOutput("Looks like $sourceToCompress already compressed to zip file")
      return
    }

    if (outputArchive.exists())
      outputArchive.toFile().deleteRecursively()

    val outputArchiveParentDir = outputArchive.parent.apply { createDirectories() }

    val archiver = ArchiverFactory.createArchiver(ArchiveFormat.ZIP)
    archiver.create(outputArchive.nameWithoutExtension, outputArchiveParentDir.toFile(), sourceToCompress.toFile())
  }

  fun unpackZip(zipFile: Path, targetDir: Path, map: (name: String) -> String? = { it }) {
    try {
      targetDir.createDirectories()

      JBZipFile(zipFile.toFile(), StandardCharsets.UTF_8, false, ThreeState.UNSURE).use { zip ->
        for (entry in zip.entries) {
          if (entry.isDirectory) {
            targetDir.resolve(entry.name).toFile().mkdirs()
            continue
          }
          val file = targetDir.resolve((map(entry.name) ?: continue))
          file.parent.createDirectories()
          file.outputStream().use {
            entry.inputStream.use { entryStream -> entryStream.copyTo(it) }
          }
          file.setLastModifiedTime(FileTime.fromMillis(entry.time))
        }
      }
    }
    catch (e: Throwable) {
      targetDir.toFile().deleteRecursively()
      zipFile.deleteIfExists()
      throw IOException("Failed to unpack $zipFile to $targetDir. ${e.message}", e)
    }
  }

  fun unpackIfMissing(archive: Path, targetDir: Path) {
    if (Files.isDirectory(targetDir) && Files.newDirectoryStream(targetDir).use { it.iterator().hasNext() }) {
      return
    }

    unpack(archive, targetDir)
  }

  fun unpack(archive: Path, targetDir: Path) {
    logOutput("Extracting $archive to $targetDir")
    //project archive may be empty
    Files.createDirectories(targetDir)
    val name = archive.fileName.toString()

    try {
      when {
        name.endsWith(".zip") ||
        name.endsWith(".ijx") ||
        name.endsWith(".jar") -> unpackZip(archive, targetDir)

        name.endsWith(".tar.gz") -> unpackTarGz(archive, targetDir)
        else -> error("Archive $name is not supported")
      }
    }
    catch (e: IOException) {
      if (e.message?.contains("No space left on device") == true) {
        throw IOException(buildString {
          appendLine("No space left while extracting $archive to $targetDir")
          appendLine(Files.getFileStore(targetDir).getDiskInfo())
          appendLine(getDiskUsageDiagnostics())
        })
      }

      throw e
    }
  }

  fun compressToTar(source: Path, outputArchive: Path, compressionType: CompressionType? = null) {
    val archiver = if (compressionType == null) ArchiverFactory.createArchiver(ArchiveFormat.TAR)
    else ArchiverFactory.createArchiver(ArchiveFormat.TAR, compressionType)

    val outputArchiveParentDir = outputArchive.parent.apply { createDirectories() }
    archiver.create(outputArchive.nameWithoutExtension, outputArchiveParentDir.toFile(), source.toFile())
  }

  fun unpackTarGz(tarFile: File, targetDir: File) {
    targetDir.deleteRecursively()
    unpackTarGz(tarFile.toPath(), targetDir.toPath())
  }

  // TODO: use com.intellij.platform.eel.EelApi.getArchive when it's ready?
  private fun unpackTarGz(tarFile: Path, targetDir: Path) {
    require(tarFile.fileName.toString().endsWith(".tar.gz")) { "File $tarFile must be tar.gz archive" }

    try {
      if (SystemInfo.isWindows) {
        val archiver = ArchiverFactory.createArchiver(ArchiveFormat.TAR, CompressionType.GZIP)
        archiver.extract(tarFile.toFile(), targetDir.toFile())
      }
      else if (SystemInfo.isLinux || SystemInfo.isMac) {
        Files.createDirectories(targetDir)
        ProcessExecutor(
          presentableName = "extract-tar",
          workDir = targetDir,
          timeout = 10.minutes,
          stderrRedirect = ExecOutputRedirect.ToStdOut("tar"),
          args = listOf("tar", "-z", "-x", "-f", tarFile.toAbsolutePath().toString(), "-C", targetDir.toAbsolutePath().toString())
        ).start()
      }
    }
    catch (e: Exception) {
      targetDir.toFile().deleteRecursively()
      tarFile.deleteIfExists()
      throw Exception("Failed to unpack $tarFile. ${e.message}. File and unpack targets are removed.", e)
    }
  }

  fun Path.zippedLength(): Long {
    if (!isRegularFile()) {
      return 0
    }

    val output = object : OutputStream() {
      var count = 0L
      override fun write(b: Int) {
        count++
      }

      override fun write(b: ByteArray) {
        count += b.size
      }

      override fun write(b: ByteArray, off: Int, len: Int) {
        count += len
      }
    }

    GZIPOutputStream(output).use { zipStream ->
      this.inputStream().use { it.copyTo(zipStream, 1024 * 1024) }
    }

    return output.count
  }

  fun Path.getFileOrDirectoryPresentableSize(): String {
    val size: Long = if (this.toFile().isFile) {
      this.toFile().length()
    }
    else {
      Files.walk(this).use { pathStream ->
        pathStream.mapToLong { p: Path ->
          if (p.toFile().isFile) {
            p.toFile().length()
          }
          else 0
        }.sum()
      }
    }
    return size.formatSize()
  }

  fun Path.getDirectoryTreePresentableSizes(depth: Int = 1): String {
    val thisPath = this
    return buildString {
      Files.walk(thisPath, depth).use { dirStream ->
        dirStream.forEach { child ->
          if (child == thisPath) {
            appendLine("Total size: ${thisPath.getFileOrDirectoryPresentableSize()}")
          }
          else {
            val indent = "  ".repeat(thisPath.relativize(child).nameCount)
            appendLine("$indent${thisPath.relativize(child)}: " + child.getFileOrDirectoryPresentableSize())
          }
        }
      }
    }
  }

  fun getDiskUsageDiagnostics(): String {
    val paths = GlobalPaths.instance

    return buildString {
      appendLine("Disk usage by integration tests (home ${paths.testHomePath})")
      appendLine(Files.getFileStore(paths.testHomePath).getDiskInfo())
      appendLine()
      appendLine(paths.testHomePath.getDirectoryTreePresentableSizes(3))
      if (paths.localCacheDirectory != paths.testHomePath / "cache") {
        appendLine("Agent persistent cache directory disk usage ${paths.localCacheDirectory}")
        appendLine(paths.localCacheDirectory.getDirectoryTreePresentableSizes(2))
      }
      appendLine()
      appendLine("Directories' size from ${paths.devServerDirectory}")
      appendLine(paths.devServerDirectory.getDirectoryTreePresentableSizes())
    }
  }

  fun Path.isFileUpToDate(): Boolean {
    if (!this.isRegularFile()) {
      logOutput("File $this does not exist")
      return false
    }
    return if (this.fileSize() <= 0) {
      logOutput("File $this is empty")
      false
    }
    else {
      this.isUpToDate()
    }
  }

  fun Path.isDirUpToDate(): Boolean {
    if (!this.isDirectory()) {
      logOutput("Path $this does not exist")
      return false
    }
    return if (this.fileSize() <= 0) {
      logOutput("Project dir $this is empty")
      false
    }
    else {
      this.isUpToDate()
    }
  }

  private fun Path.isUpToDate(): Boolean {
    val lastModified = this.toFile().lastModified()
    val currentTime = System.currentTimeMillis()

    // less then a day ago
    val upToDate = currentTime - lastModified < 24 * 60 * 60 * 1000
    if (upToDate) {
      logOutput("$this is up to date")
    }
    else {
      logOutput("$this is not up to date")
    }
    return upToDate
  }
}

fun FileStore.getDiskInfo(): String = buildString {
  appendLine("Disk info of ${name()}")
  appendLine("  Total space: " + totalSpace.formatSize())
  appendLine("  Unallocated space: " + unallocatedSpace.formatSize())
  appendLine("  Usable space: " + usableSpace.formatSize())
}