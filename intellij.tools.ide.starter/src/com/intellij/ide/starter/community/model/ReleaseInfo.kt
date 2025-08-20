package com.intellij.ide.starter.community.model

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate


data class ReleaseInfo(
  val date: LocalDate,
  val type: String,
  val version: String,
  val majorVersion: String,
  val build: String,
  val downloads: Download,
)

data class Download(
  @JsonAlias("linux_x64")
  val linux: OperatingSystem?,

  @JsonAlias("linux_aarch64")
  @JsonProperty("linuxARM64")
  val linuxArm: OperatingSystem?,

  @JsonAlias("macos_x64")
  val mac: OperatingSystem?,

  @JsonAlias("macos_aarch64")
  val macM1: OperatingSystem?,

  @JsonAlias("windows_x64")
  val windows: OperatingSystem?,

  @JsonAlias("windows_zip_x64")
  val windowsZip: OperatingSystem?,

  // TODO: Probably installation will not be supported in Starter framework (because Starter relies on archive for windows)
  @JsonAlias("windows_aarch64")
  @JsonProperty("windowsARM64")
  val windowsArm: OperatingSystem?,
)

data class OperatingSystem(val link: String)
