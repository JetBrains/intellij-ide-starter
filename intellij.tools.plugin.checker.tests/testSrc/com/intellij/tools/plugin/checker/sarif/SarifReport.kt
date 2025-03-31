package com.intellij.tools.plugin.checker.sarif

import com.fasterxml.jackson.annotation.JsonProperty

data class SarifReport(
    @JsonProperty("\$schema")
    val schema: String = "https://schemastore.azurewebsites.net/schemas/json/sarif-2.1.0-rtm.6.json",
    val version: String = "2.1.0",
    val runs: List<Run> = emptyList()
)

data class Run(
    val tool: Tool,
    val artifacts: List<Artifact> = emptyList(),
    val results: List<Result> = emptyList()
)

data class Tool(
    val driver: Driver
)

data class Driver(
    val name: String,
    val informationUri: String,
    val semanticVersion: String
)

data class Artifact(
    val location: Location
)

data class Location(
    val uri: String
)

data class Result(
    val ruleId: String,
    val message: Message
)

data class Message(
    val text: String
)
