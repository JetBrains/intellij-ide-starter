import org.gradle.kotlin.dsl.intellijPlatform
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
plugins {
  id("org.jetbrains.intellij.platform") version "2.3.0"
  kotlin("jvm") version "2.3.0"
}

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

dependencies {
  intellijPlatform {
    intellijIdeaUltimate("LATEST-EAP-SNAPSHOT", useInstaller = false)
  }
}

intellijPlatform {
  pluginConfiguration {
    ideaVersion {
      // Remove the upper bound to avoid blocking future builds on this baseline
      untilBuild.set(provider { null })
      sinceBuild.set("243")
    }
  }
}

kotlin {
  jvmToolchain(21)
}

// Expose the prepared plugin sandbox directory path to other modules
tasks.named<PrepareSandboxTask>("prepareSandbox") {
  doLast {
    val outFile = project.layout.buildDirectory.file("plugin-sandbox-dir.txt").get().asFile
    outFile.parentFile.mkdirs()
    outFile.writeText(pluginDirectory.get().asFile.absolutePath)
  }
}