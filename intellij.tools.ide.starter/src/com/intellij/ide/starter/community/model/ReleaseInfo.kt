package com.intellij.ide.starter.community.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate


data class ReleaseInfo(val date: LocalDate,
                       val type: String,
                       val version: String,
                       val majorVersion: String,
                       val build: String,
                       val downloads: Download)

data class Download(val linux: OperatingSystem?,
                    @JsonProperty("linuxARM64")
                    val linuxArm: OperatingSystem?,
                    val mac: OperatingSystem?,
                    val macM1: OperatingSystem?,
                    val windows: OperatingSystem?,
                    val windowsZip: OperatingSystem?,
                    // TODO: Probably installation will not be supported in Starter framework (because Starter relies on archive for windows)
                    @JsonProperty("windowsARM64")
                    val windowsArm: OperatingSystem?)

data class OperatingSystem(val link: String)
