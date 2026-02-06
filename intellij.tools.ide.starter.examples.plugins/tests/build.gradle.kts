plugins {
  kotlin("jvm") version "2.3.0"
}

repositories {
  mavenCentral()
  maven(url = "https://cache-redirector.jetbrains.com/intellij-dependencies")
  maven(url = "https://www.jetbrains.com/intellij-repository/releases")
  maven(url = "https://www.jetbrains.com/intellij-repository/snapshots")
  maven(url = "https://download.jetbrains.com/teamcity-repository")
  maven(url = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/grazi/grazie-platform-public")
}

kotlin {
  jvmToolchain(21)
}

dependencies {

  // If `intellijPlatform { defaultRepositories() }`
  // is used, it causes jvm crash or requires filtering out of -javaagent:.../coroutines-javaagent.jar

  // IDE Starter / Driver stack
  testImplementation("com.jetbrains.intellij.tools:ide-starter-squashed:LATEST-EAP-SNAPSHOT")
  testImplementation("com.jetbrains.intellij.tools:ide-starter-junit5:LATEST-EAP-SNAPSHOT")
  testImplementation("com.jetbrains.intellij.tools:ide-metrics-collector:LATEST-EAP-SNAPSHOT")
  testImplementation("com.jetbrains.intellij.tools:ide-metrics-collector-starter:LATEST-EAP-SNAPSHOT")
  testImplementation("com.jetbrains.intellij.tools:ide-performance-testing-commands:LATEST-EAP-SNAPSHOT")
  testImplementation("com.jetbrains.intellij.tools:ide-starter-driver:LATEST-EAP-SNAPSHOT")
  testImplementation("com.jetbrains.intellij.driver:driver-client:LATEST-EAP-SNAPSHOT")
  testImplementation("com.jetbrains.intellij.driver:driver-sdk:LATEST-EAP-SNAPSHOT")
  testImplementation("com.jetbrains.intellij.driver:driver-model:LATEST-EAP-SNAPSHOT")

  // JUnit 5
  val junitBom = platform("org.junit:junit-bom:5.12.2")
  testImplementation(junitBom)
  testImplementation("org.junit.jupiter:junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")

  // DI / Coroutines used by tests or frameworks
  testImplementation("org.kodein.di:kodein-di-jvm:7.20.2")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.1")
}

tasks.test {
  // Ensure the plugin is built and sandboxed before running tests
  dependsOn(":plugin:prepareSandbox")

  // Provide path to the prepared plugin sandbox directory for tests (read from file produced by :plugin)
  doFirst {
    val dirFile = project(":plugin").layout.buildDirectory.file("plugin-sandbox-dir.txt").get().asFile
    val path = dirFile.readText().trim()
    systemProperty("path.to.build.plugin", path)
  }

  // Enable JUnit 5 platform so JUnit Jupiter tests are discovered and executed
  useJUnitPlatform()

  testLogging {
    events("passed", "skipped", "failed", "standardOut", "standardError")
  }
}
