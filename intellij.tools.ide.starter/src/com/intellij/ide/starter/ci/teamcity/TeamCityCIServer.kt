package com.intellij.ide.starter.ci.teamcity

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.utils.logOutput
import org.kodein.di.direct
import org.kodein.di.instance
import java.net.URI
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader
import kotlin.math.min

fun CIServer.asTeamCity(): TeamCityCIServer = this as TeamCityCIServer

open class TeamCityCIServer(
  private val systemPropertiesFilePath: Path? = try {
    Path(System.getenv("TEAMCITY_BUILD_PROPERTIES_FILE"))
  }
  catch (_: Exception) {
    null
  }
) : CIServer {
  override fun publishArtifact(source: Path, artifactPath: String, artifactName: String) {
    TeamCityClient.publishTeamCityArtifacts(source = source, artifactPath = artifactPath, artifactName = artifactName)
  }

  override fun reportTestFailure(testName: String, message: String, details: String) {
    val flowId = UUID.randomUUID().toString()

    val generifiedTestName = testName.processStringForTC()

    logOutput(String.format("##teamcity[testStarted name='%s' flowId='%s' nodeId='${generifiedTestName}' parentNodeId='0']", generifiedTestName, flowId))
    logOutput(String.format(
      "##teamcity[testFailed name='%s' message='%s' details='%s' flowId='%s' nodeId='${generifiedTestName}' parentNodeId='0']",
      generifiedTestName, message.processStringForTC(), details.processStringForTC(), flowId
    ))
    logOutput(String.format("##teamcity[testFinished name='%s' flowId='%s' nodeId='${generifiedTestName}' parentNodeId='0']", generifiedTestName, flowId))
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

  private val listOfPatternsWhichShouldBeIgnored by lazy {
    val ignoredPattern = System.getenv("IGNORED_TEST_FAILURE_PATTERN")
    val patterns = mutableListOf(
      "No files have been downloaded for .+:.+".toRegex(),
      "Library '.+' resolution failed".toRegex(),
      "Too many IDE internal errors. Monitoring stopped.".toRegex(),
      "Invalid folding descriptor detected".toRegex()
    )
    if (ignoredPattern != null && ignoredPattern.isNotBlank()) {
      logOutput("Add $ignoredPattern ignored pattern from env")
      patterns.add(ignoredPattern.toRegex())
    }
    patterns
  }

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
    systemPropertiesFilePath?.let { props.putAll(loadProperties(it)) }

    props.putAll(System.getProperties().map { it.key.toString() to it.value.toString() })
    props
  }

  /**
   * @return String or Null if parameters isn't found
   */
  private fun getBuildParam(name: String, impreciseNameMatch: Boolean = false): String? {
    val totalParams = systemProperties.plus(buildParams)

    val paramValue = if (impreciseNameMatch) {
      val paramCandidates = totalParams.filter { it.key.contains(name) }
      if (paramCandidates.size > 1) System.err.println("Found many parameters matching $name. Candidates: $paramCandidates")
      paramCandidates[paramCandidates.toSortedMap().firstKey()]
    }
    else totalParams[name]

    return paramValue
  }

  override val isBuildRunningOnCI = System.getenv("TEAMCITY_VERSION") != null
  override val buildNumber by lazy { System.getenv("BUILD_NUMBER") ?: "" }
  override val branchName by lazy { buildParams["teamcity.build.branch"] ?: "" }

  val configurationName by lazy { getBuildParam("teamcity.buildConfName") }
  val buildVcsNumber by lazy {getBuildParam("build.vcs.number") ?: "Unknown"}
  val buildConfigName: String? by lazy {getBuildParam("teamcity.buildConfName")}
  override val buildParams by lazy {
    val configurationPropertiesFile = systemProperties["teamcity.configuration.properties.file"]

    if (configurationPropertiesFile.isNullOrBlank()) return@lazy emptyMap()
    loadProperties(Path(configurationPropertiesFile))
  }

  /** Root URI of the server */
  val serverUri: URI by lazy {
    return@lazy di.direct.instance<URI>(tag = "teamcity.uri")
  }

  val userName: String by lazy { getBuildParam("teamcity.auth.userId")!! }
  val password: String by lazy { getBuildParam("teamcity.auth.password")!! }

  private val isDefaultBranch by lazy {
    //see https://www.jetbrains.com/help/teamcity/predefined-build-parameters.html#PredefinedBuildParameters-Branch-RelatedParameters
    hasBooleanProperty("teamcity.build.branch.is_default", default = false)
  }

  val isPersonalBuild by lazy {
    getBuildParam("build.is.personal").equals("true", ignoreCase = true)
  }

  val buildId: String by lazy {
    getBuildParam("teamcity.build.id") ?: "LOCAL_RUN_SNAPSHOT"
  }
  val teamcityAgentName by lazy { getBuildParam("teamcity.agent.name") }
  val teamcityCloudProfile by lazy { getBuildParam("system.cloud.profile_id") }

  val buildTypeId: String? by lazy { getBuildParam("teamcity.buildType.id") }

  fun buildUrl(): String = "$serverUri/buildConfiguration/$buildTypeId/$buildId?buildTab=tests"

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

  fun hasBooleanProperty(key: String, default: Boolean) = getBuildParam(key)?.equals("true", ignoreCase = true) ?: default

  fun isSafePush(): Boolean {
    val isSafePush = System.getenv("SAFE_PUSH")
    return (isSafePush != null && isSafePush == "true")
  }

  companion object {
    fun String.processStringForTC(): String {
      return this.substring(0, min(7000, this.length))
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

    fun reportTeamCityMessage(text: String) {
      logOutput(" ##teamcity[message text='$text']")
    }
  }
}