import org.gradle.kotlin.dsl.intellijPlatform
import org.gradle.kotlin.dsl.register
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.models.*
import org.jetbrains.intellij.platform.gradle.tasks.*
import java.util.*

plugins {
  id("org.jetbrains.intellij.platform") version "2.3.0"
  kotlin("jvm") version "2.2.0"
}

repositories {
  mavenCentral()

  intellijPlatform {
    defaultRepositories()
  }
}


sourceSets {
  create("integrationTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
  }
}

val integrationTestImplementation by configurations.getting {
  extendsFrom(configurations.testImplementation.get())
}
val integrationTestRuntimeOnly by configurations.getting {
  extendsFrom(configurations.testRuntimeOnly.get())
}

dependencies {
  intellijPlatform {
    // Use the latest IntelliJ Platform EAP snapshot
    intellijIdeaUltimate("LATEST-EAP-SNAPSHOT", useInstaller = false)
    testFramework(TestFrameworkType.Starter)
  }
  testRuntimeOnly(kotlin("stdlib"))
  testRuntimeOnly(kotlin("reflect"))

  integrationTestRuntimeOnly(kotlin("stdlib"))
  integrationTestRuntimeOnly(kotlin("reflect"))

  val junitBom = platform("org.junit:junit-bom:5.12.2")
  integrationTestImplementation(junitBom)
  integrationTestImplementation("org.junit.jupiter:junit-jupiter")
  integrationTestRuntimeOnly("org.junit.platform:junit-platform-launcher")
  integrationTestImplementation("org.kodein.di:kodein-di-jvm:7.20.2")
  integrationTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.1")
}

intellijPlatform {
  pluginConfiguration {
    ideaVersion {
      // Let sinceBuild be derived from the selected platform automatically
      // Remove the upper bound to avoid blocking future builds on this baseline
      untilBuild.set(provider { null })
    }
  }
}

kotlin {
  jvmToolchain(21)
}

val integrationTest = tasks.register<Test>("integrationTest") {
  val integrationTestSourceSet = sourceSets.getByName("integrationTest")
  testClassesDirs = integrationTestSourceSet.output.classesDirs
  classpath = integrationTestSourceSet.runtimeClasspath
  systemProperty("path.to.build.plugin", tasks.prepareSandbox.get().pluginDirectory.get().asFile)
  useJUnitPlatform()
  dependsOn(tasks.prepareSandbox)
}