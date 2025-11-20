package com.intellij.tools.plugin.checker.marketplace

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.intellij.ide.starter.ci.teamcity.TeamCityClient
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.report.Error
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.tools.plugin.checker.aws.SqsClientWrapper
import com.intellij.tools.plugin.checker.aws.VerificationMessage
import com.intellij.tools.plugin.checker.aws.VerificationResultType
import com.intellij.tools.plugin.checker.di.teamCityIntelliJPerformanceServer
import com.intellij.tools.plugin.checker.sarif.Artifact
import com.intellij.tools.plugin.checker.sarif.Driver
import com.intellij.tools.plugin.checker.sarif.Location
import com.intellij.tools.plugin.checker.sarif.Message
import com.intellij.tools.plugin.checker.sarif.Result
import com.intellij.tools.plugin.checker.sarif.Run
import com.intellij.tools.plugin.checker.sarif.SarifReport
import com.intellij.tools.plugin.checker.sarif.Tool
import software.amazon.awssdk.regions.Region
import java.io.File
import java.nio.file.Path

abstract class MarketplaceReporter {

  protected fun classifyErrors(errors: List<Error>): VerificationResultType {
    return when {
      errors.isEmpty() -> VerificationResultType.OK
      errors.size == 1 && errors.first().messageText.contains("due to a dialog being shown") ->
        VerificationResultType.WARNINGS
      else -> VerificationResultType.PROBLEMS
    }
  }

  protected fun generateVerdict(errors: List<Error>): String {
    return when {
      errors.isEmpty() -> "No issues occurred during the IDE run with the plugin installed"
      else -> "${errors.size} ${if (errors.size == 1) "issue" else "issues"} occurred during the IDE run with the plugin installed"
    }
  }


  protected fun sendSqsMessage(message: VerificationMessage) {
    if (!teamCityIntelliJPerformanceServer.isBuildRunningOnCI) return

    val sqsClient = SqsClientWrapper(
      "https://sqs.eu-west-1.amazonaws.com/046864285642/production__external-services",
      Region.EU_WEST_1
    )

    try {
      sqsClient.sendMessage(message)
    } finally {
      sqsClient.closeClient()
    }
  }

  protected fun generateSarifReport(
    errors: List<Error>,
    semanticVersion: String,
    artifactsLocation: String,
    sarifPath: Path,
    artifactPath: String
  ) {
    val sarifReport = SarifReport(
      runs = listOf(
        Run(
          tool = Tool(
            driver = Driver(
              name = "IntellijIdeStarter",
              informationUri = "https://github.com/JetBrains/intellij-ide-starter",
              semanticVersion = semanticVersion
            )
          ),
          artifacts = listOf(Artifact(Location(artifactsLocation))),
          results = errors.map { error ->
            Result(
              ruleId = error.messageText,
              message = Message(text = error.stackTraceContent)
            )
          }
        )
      )
    )

    val mapper = jsonMapper {
      addModule(kotlinModule())
      enable(SerializationFeature.INDENT_OUTPUT)
    }

    val artifactsDir = GlobalPaths.instance.artifactsDirectory
    logOutput("##teamcity[setParameter name='starter.sarif.reports.path' value='$artifactsDir/sarif-reports']")
    logOutput("##teamcity[progressMessage 'Writing SARIF report to $sarifPath']")
    mapper.writeValue(File(sarifPath.toString()), sarifReport)

    logOutput("##teamcity[setParameter name='starter.upload.sarif' value='true']")

    TeamCityClient.publishTeamCityArtifacts(
      source = sarifPath,
      artifactPath = artifactPath,
      artifactName = "sarif.json",
      zipContent = false
    )
  }
}
