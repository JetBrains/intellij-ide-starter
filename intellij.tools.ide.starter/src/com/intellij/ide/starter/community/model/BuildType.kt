package com.intellij.ide.starter.community.model

enum class BuildType(val type: String) {
  RELEASE("release"),
  EAP("eap"),
  PREVIEW("preview"),
  NIGHTLY("nightly"),
  RC("rc")
}