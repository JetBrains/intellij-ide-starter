package com.intellij.ide.starter.runner

import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

data class ContainerConfig(
  val jmxPort: Int = 7777,
  val rmiPort: Int = 7778,
  val dockerImage: String = "idea",
  val vmOptions: String?,
  val testProject: String? = null,
  val workingDir: String? = null,
  val arguments: List<String> = emptyList(),
)

class DockerIDEHandle(private val container: IdeContainer) : IDEHandle {
  override val id: String
    get() = container.container.containerId
  override val isAlive: Boolean
    get() = container.container.isRunning

  override fun kill() {
    container.container.stop()
  }
}

class IdeContainer(val config: ContainerConfig) {
  val container = GenericContainer(DockerImageName.parse(config.dockerImage))
    .withLogConsumer {
      println(it.utf8String)
    }

  init {
    if (config.testProject != null) {
      container.withFileSystemBind(
        config.testProject,
        "/test-project",
        BindMode.READ_WRITE
      )
    }
    if (config.workingDir != null) {
      container.withFileSystemBind(
        config.workingDir,
        "/home/developer/workingDir", BindMode.READ_WRITE
      )
    }
    container.withFileSystemBind(
      config.vmOptions,
      "/opt/idea/bin/idea64.vmoptions",
      BindMode.READ_WRITE
    )
    container.portBindings = listOf("${config.jmxPort}:${config.jmxPort}", "${config.rmiPort}:${config.rmiPort}")
    container.waitingFor(Wait.forHealthcheck())
    if (!config.arguments.isEmpty()) {
      val mapped = config.arguments.map {
        it.replace(config.testProject.toString()
                     .replace(config.testProject.toString().substringBefore("/out"), "/home/developer/workingDir"), "/test-project")
      }
      val arguments: List<String> = listOf("Xvfb", ":0", "-screen", "0", "1920x1080x24", "-ac", "+extension", "GLX", "-noreset", "&", "/opt/idea/bin/idea") + mapped
      val finalArguments: List<String> = listOf("/bin/sh", "-c", arguments.joinToString(" "))
      container.withCreateContainerCmdModifier { cmd -> cmd.withCmd(finalArguments) }
    }
  }

  fun driverHost(): String = "localhost:${config.jmxPort}"

}
