import org.gradle.kotlin.dsl.intellijPlatform
import org.jetbrains.intellij.platform.gradle.*

plugins {
  id("org.jetbrains.intellij.platform") version "2.11.0"
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

val integrationTestImplementation: Configuration by configurations.getting {
  extendsFrom(configurations.testImplementation.get())
}
val integrationTestRuntimeOnly by configurations.getting {
  extendsFrom(configurations.testRuntimeOnly.get())
}

dependencies {
  intellijPlatform {
    // Use the latest IntelliJ Platform EAP snapshot
    intellijIdeaUltimate("LATEST-EAP-SNAPSHOT") { useInstaller = false }
    testFramework(TestFrameworkType.Starter, "LATEST-EAP-SNAPSHOT", configurationName = "integrationTestImplementation")
  }
  testRuntimeOnly(kotlin("stdlib"))
  testRuntimeOnly(kotlin("reflect"))

  val junitBom = platform("org.junit:junit-bom:5.10.3")
  integrationTestImplementation(junitBom)
  integrationTestImplementation("org.junit.jupiter:junit-jupiter")
  integrationTestRuntimeOnly("org.junit.platform:junit-platform-launcher")

  integrationTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.1")
}

intellijPlatform {
  pluginConfiguration {
    ideaVersion {
      sinceBuild.set("243")
      // Remove the upper bound to avoid blocking future builds on this baseline
      untilBuild.set(provider { null })
    }
  }
}

kotlin {
  jvmToolchain(21)
}

val integrationTest by intellijPlatformTesting.testIdeUi.registering {
  task {
    val integrationTestSourceSet = sourceSets.getByName("integrationTest")
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath

    systemProperty("path.to.build.plugin", tasks.prepareSandbox.get().pluginDirectory.get().asFile)
    useJUnitPlatform()
    dependsOn(tasks.prepareSandbox)

    // Make test execution visible in console
    testLogging {
      events("passed", "skipped", "failed")
      showStandardStreams = true
    }
  }
}