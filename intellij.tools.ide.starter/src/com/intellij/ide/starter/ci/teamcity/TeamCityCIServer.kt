package com.intellij.ide.starter.ci.teamcity

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.utils.logOutput
import java.net.URI
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader

fun CIServer.asTeamCity(): TeamCityCIServer = this as TeamCityCIServer

open class TeamCityCIServer(
  val fallbackUri: URI
) : CIServer {
  override fun publishArtifact(source: Path, artifactPath: String, artifactName: String) {
    TeamCityClient.publishTeamCityArtifacts(source = source, artifactPath = artifactPath, artifactName = artifactName)
  }

  override fun reportTestFailure(testName: String, message: String, details: String) {
    val flowId = UUID.randomUUID().toString()

    val generifiedTestName = testName.processStringForTC()

    logOutput(String.format("##teamcity[testStarted name='%s' flowId='%s']", generifiedTestName, flowId))
    logOutput(String.format(
      "##teamcity[testFailed name='%s' message='%s' details='%s' flowId='%s']",
      generifiedTestName, message.processStringForTC(), details.processStringForTC(), flowId
    ))
    logOutput(String.format("##teamcity[testFinished name='%s' flowId='%s']", generifiedTestName, flowId))
  }

  override fun ignoreTestFailure(testName: String, message: String, details: String) {
    val flowId = UUID.randomUUID().toString()
    val generifiedTestName = testName.processStringForTC()
    logOutput(String.format(
      "##teamcity[testIgnored name='%s' message='%s' details='%s' flowId='%s']",
      generifiedTestName, message.processStringForTC(), details.processStringForTC(), flowId
    ))
  }

  override fun isTestFailureShouldBeIgnored(message: String): Boolean {
    listOfPatternsWhichShouldBeIgnored.forEach { pattern ->
      if (pattern.containsMatchIn(message)) {
        return true
      }
    }
    return false
  }

  private val listOfPatternsWhichShouldBeIgnored = listOf(
    "No files have been downloaded for .+:.+".toRegex(),
    "Library '.+' resolution failed".toRegex()
  )

  private fun loadProperties(propertiesPath: Path): Map<String, String> =
    try {
      propertiesPath.bufferedReader().use {
        val map = mutableMapOf<String, String>()
        val ps = Properties()
        ps.load(it)

        ps.forEach { k, v ->
          if (k != null && v != null) {
            map[k.toString()] = v.toString()
          }
        }
        map
      }
    }
    catch (t: Throwable) {
      emptyMap()
    }

  private val systemProperties by lazy {
    val props = mutableMapOf<String, String>()
    System.getenv("TEAMCITY_BUILD_PROPERTIES_FILE")?.let { props.putAll(loadProperties(Path(it))) }

    props.putAll(System.getProperties().map { it.key.toString() to it.value.toString() })
    props
  }

  private fun getExistingParameter(name: String, impreciseNameMatch: Boolean = false): String {
    val totalParams = systemProperties.plus(buildParams)

    val paramValue = if (impreciseNameMatch) {
      val paramCandidates = totalParams.filter { it.key.contains(name) }
      if (paramCandidates.size > 1) System.err.println("Found many parameters matching $name. Candidates: $paramCandidates")
      paramCandidates[paramCandidates.toSortedMap().firstKey()]
    }
    else totalParams[name]

    return paramValue ?: error("Parameter $name is not specified in the build!")
  }

  override val isBuildRunningOnCI = System.getenv("TEAMCITY_VERSION") != null
  override val buildNumber by lazy { System.getenv("BUILD_NUMBER") ?: "" }
  override val branchName by lazy { buildParams["teamcity.build.branch"] ?: "" }

  val configurationName by lazy { systemProperties["teamcity.buildConfName"] }

  override val buildParams by lazy {
    val configurationPropertiesFile = systemProperties["teamcity.configuration.properties.file"]

    if (configurationPropertiesFile.isNullOrBlank()) return@lazy emptyMap()
    loadProperties(Path(configurationPropertiesFile))
  }

  /** Root URI of the server */
  val serverUri: URI by lazy {
    systemProperties["teamcity.serverUrl"]?.let { return@lazy URI(it).normalize() }
    return@lazy fallbackUri
  }

  val userName: String by lazy { getExistingParameter("teamcity.auth.userId") }
  val password: String by lazy { getExistingParameter("teamcity.auth.password") }

  private val isDefaultBranch by lazy {
    //see https://www.jetbrains.com/help/teamcity/predefined-build-parameters.html#PredefinedBuildParameters-Branch-RelatedParameters
    hasBooleanProperty("teamcity.build.branch.is_default", default = false)
  }

  val isPersonalBuild by lazy {
    systemProperties["build.is.personal"].equals("true", ignoreCase = true)
  }

  val buildId: String by lazy {
    buildParams["teamcity.build.id"] ?: run { "LOCAL_RUN_SNAPSHOT" }
  }
  val teamcityAgentName by lazy { buildParams["teamcity.agent.name"] }
  val teamcityCloudProfile by lazy { buildParams["system.cloud.profile_id"] }

  val buildTypeId: String? by lazy { systemProperties["teamcity.buildType.id"] }

  val isSpecialBuild: Boolean
    get() {
      if (!isBuildRunningOnCI) {
        logOutput("[Metrics Publishing] Not running build on TeamCity => DISABLED")
        return true
      }

      if (isPersonalBuild) {
        logOutput("[Metrics Publishing] Personal builds are ignored => DISABLED")
        return true
      }

      if (!isDefaultBranch) {
        logOutput("[Metrics Publishing] Non default branches builds are ignored => DISABLED")
        return true
      }

      return false
    }

  fun hasBooleanProperty(key: String, default: Boolean) = buildParams[key]?.equals("true", ignoreCase = true) ?: default

  companion object {
    fun String.processStringForTC(): String {
      return this.substring(0, Math.min(7000, this.length))
        .replace("\\|", "||")
        .replace("\\[", "|[")
        .replace("]", "|]")
        .replace("\n", "|n")
        .replace("'", "|'")
        .replace("\r", "|r")
    }

    fun setStatusTextPrefix(text: String) {
      logOutput(" ##teamcity[buildStatus text='$text {build.status.text}'] ")
    }

    fun reportTeamCityStatistics(key: String, value: Int) {
      logOutput(" ##teamcity[buildStatisticValue key='${key}' value='${value}']")
    }

    fun reportTeamCityStatistics(key: String, value: Long) {
      logOutput(" ##teamcity[buildStatisticValue key='${key}' value='${value}']")
    }
  }
}