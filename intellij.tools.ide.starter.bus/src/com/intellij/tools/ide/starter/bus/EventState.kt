package com.intellij.tools.ide.starter.bus

enum class EventState {
  UNDEFINED,

  /** Right before the action */
  BEFORE,

  /** Right before kill the ide process */
  BEFORE_KILL,

  /** After the action was completed */
  AFTER,

  /** When action started */
  IN_TIME
}