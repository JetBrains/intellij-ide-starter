package com.intellij.ide.starter.ide.command

/**
 * One or more commands, that will be "played" in sequence by IDE
 */
open class CommandChain : MarshallableCommand, Iterable<MarshallableCommand> {
  private val _chain = mutableListOf<MarshallableCommand>()

  override fun iterator(): Iterator<MarshallableCommand> = _chain.iterator()

  override fun storeToString(): String {
    return _chain.joinToString(separator = System.lineSeparator()) { it.storeToString() }
  }

  /**
   * Pattern for adding a command: %YOUR_COMMAND_PREFIX COMMAND_PARAMS
   */
  fun addCommand(command: String): CommandChain {
    _chain.add(initMarshallableCommand(command))
    return this
  }

  fun addCommand(command: MarshallableCommand): CommandChain {
    _chain.add(command)
    return this
  }

  /**
   * Pattern for adding a command: %YOUR_COMMAND_PREFIX COMMAND_PARAM_1 .. COMMAND_PARAM_N
   */
  fun addCommand(vararg commandArgs: String): CommandChain {
    val command = initMarshallableCommand(commandArgs.joinToString(separator = " "))
    _chain.add(command)
    return this
  }

  fun addCommandChain(commandChain: CommandChain): CommandChain {
    _chain.addAll(commandChain)
    return this
  }

  private fun initMarshallableCommand(content: String): MarshallableCommand =
    object : MarshallableCommand {
      override fun storeToString(): String = content
    }
}