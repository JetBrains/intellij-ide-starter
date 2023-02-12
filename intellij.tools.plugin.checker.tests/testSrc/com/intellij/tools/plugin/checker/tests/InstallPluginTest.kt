package com.intellij.tools.plugin.checker.tests

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.treeToValue
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.teamcity.TeamCityClient
import com.intellij.ide.starter.ci.teamcity.asTeamCity
import com.intellij.ide.starter.ci.teamcity.withAuth
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.downloadLatestAndroidSdk
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.ide.command.CommandChain
import com.intellij.ide.starter.junit5.JUnit5StarterAssistant
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.runner.TestContainerImpl
import com.intellij.ide.starter.sdk.JdkDownloaderFacade
import com.intellij.ide.starter.sdk.setupAndroidSdkToProject
import com.intellij.ide.starter.system.OsType
import com.intellij.ide.starter.system.SystemInfo
import com.intellij.ide.starter.utils.logOutput
import com.intellij.tools.plugin.checker.data.TestCases
import com.intellij.tools.plugin.checker.di.initPluginCheckerDI
import com.intellij.tools.plugin.checker.di.teamCityIntelliJPerformanceServer
import com.intellij.tools.plugin.checker.marketplace.MarketplaceEvent
import com.jetbrains.performancePlugin.commands.chain.exitApp
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.kodein.di.direct
import org.kodein.di.instance
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.*
import java.util.concurrent.TimeUnit


@ExtendWith(JUnit5StarterAssistant::class)
class InstallPluginTest {

  private lateinit var testInfo: TestInfo
  private lateinit var container: TestContainerImpl

  companion object {
    private fun setDebugBuildParamsForLocalDebug(vararg buildProperties: Pair<String, String>): Path {
      val tempPropertiesFile = File.createTempFile("teamcity_", "_properties_file.properties")

      Properties().apply {
        buildProperties.forEach { this.setProperty(it.first, it.second) }
        store(tempPropertiesFile.outputStream(), "")
      }

      return tempPropertiesFile.toPath()
    }

    init {
      if (!teamCityIntelliJPerformanceServer.isBuildRunningOnCI) {
        // use this to simplify local debug
        val systemPropertiesFilePath = setDebugBuildParamsForLocalDebug(
          Pair("teamcity.build.id", "847"),
          Pair("teamcity.auth.userId", "maxim.kolmakov"),
          Pair("teamcity.auth.password", "eyJ0eXAiOiAiVENWMiJ9.bGdPNjF2cVdNa3NrMVBlblRpWEh1TVNuSFVv.MjdlODA0NTAtMWM0MC00YmQxLWJjMTgtMTEzZGMyMTU5Yzg3")
        )
        initPluginCheckerDI(systemPropertiesFilePath)
      }
      else {
        initPluginCheckerDI()
      }
    }

    @BeforeAll
    @JvmStatic
    fun beforeAll() {
      Assumptions.assumeTrue({ data().isNotEmpty() }, "Cannot run tests, because tests cases are empty")
    }

    /**
     * Extract only 'sns_message_body' from text like this:
     * ##type='sns' triggerId='TRIGGER_1' queueMergingEnabled='false' sns_message_body='{JSON_CONTENT}'
     */
    private fun String.extractSnsMessageBody(): String {
      val matchResult = Regex("sns_message_body='(.)*'").find(this)

      requireNotNull(matchResult) { "Error happened during parsing trigger parameters. Expecting `sns_message_body` param" }

      return matchResult.groups.single { it != null && it.value.startsWith("sns_message_body") }!!
        .value.removePrefix("sns_message_body='").removeSuffix("'")
    }

    /**
     * Json we get, isn't valid. So we have to do regexp thing before deserialization
     */
    private fun String.extractMarketplaceDetailPayload(): String {
      val matchResult = Regex("\"detail\":(.)*}").find(this)

      requireNotNull(matchResult) { "Error happened during searching for detail field in trigger parameter" }

      return matchResult.groups.single { it != null && it.value.startsWith("\"detail\"") }!!
        .value.removePrefix("\"detail\":").removeSuffix("}")
    }

    private fun deserializeMessageFromMarketplace(input: String): MarketplaceEvent {
      val jacksonMapper = jsonMapper {
        addModule(kotlinModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      }

      val detailField = jacksonMapper.readTree(input.extractMarketplaceDetailPayload())
      return jacksonMapper.treeToValue<MarketplaceEvent>(detailField)
    }

    private fun getMarketplaceEvent(): MarketplaceEvent {
      val triggeredByJsonNode = TeamCityClient.run {
        get(
          fullUrl = restUri.resolve("builds/id:${di.direct.instance<CIServer>().asTeamCity().buildId}?fields=triggered(displayText)")
        ) { it.withAuth() }
      }

      val displayTextField = requireNotNull(triggeredByJsonNode.first().first().asText())

      return deserializeMessageFromMarketplace(displayTextField.extractSnsMessageBody())
    }

    @JvmStatic
    fun data(): List<EventToTestCaseParams> {
      val event = getMarketplaceEvent()
      val draftParams: EventToTestCaseParams = EventToTestCaseParams(
        event = event,
        testCase = TestCases.IU.GradleJitPackSimple
      )

      return modifyTestCaseForIdeVersion(draftParams)
    }

    fun modifyTestCaseForIdeVersion(params: EventToTestCaseParams): List<EventToTestCaseParams> {
      if (!IdeProductProvider.isProductSupported(params.event.productCode)) {
        logOutput(RuntimeException("Product ${params.event.productCode} is not supported yet. Link to download it ${params.event.productLink}"))
        return emptyList()
      }

      val link = params.event.productLink.substring(0, params.event.productLink.indexOf(".tar.gz"))
      val downloadLink: String = link + when (SystemInfo.getOsType()) {
        OsType.Linux -> ".tar.gz"
        OsType.MacOS -> {
          if (SystemInfo.OS_ARCH == "aarch64") "-aarch64.dmg"
          else ".dmg"
        }
        OsType.Windows -> ".exe"
        else -> throw RuntimeException("OS is not supported")
      }

      val ideInfo = IdeProductProvider.getProducts().single { it.productCode == params.event.productCode }
        .copy(downloadURI = URI(downloadLink), buildType = params.event.productType ?: "")

      val paramsWithAppropriateIde = params.onIDE(ideInfo)
      val numericProductVersion = paramsWithAppropriateIde.event.getNumericProductVersion()

      return listOf(paramsWithAppropriateIde.copy(testCase = paramsWithAppropriateIde.testCase.withBuildNumber(numericProductVersion)))
    }
  }

  @ParameterizedTest
  @MethodSource("data")
  @Timeout(value = 20, unit = TimeUnit.MINUTES)
  fun installPluginTest(params: EventToTestCaseParams) {
    val testContext = container
      .initializeTestContext(testName = testInfo.hyphenateWithClass(), testCase = params.testCase)
      .prepareProjectCleanImport()
      .setSharedIndexesDownload(enable = true)
      .apply {
        pluginConfigurator.setupPluginFromURL(params.event.file)
      }
      .setLicense(System.getenv("LICENSE_KEY"))
    if (params.event.productCode == di.direct.instance<IdeProductProvider>().AI.productCode) {
      logOutput("Setting up Android SDK")
      val androidSdk = downloadLatestAndroidSdk(JdkDownloaderFacade.jdk11.home)
      setupAndroidSdkToProject(testContext.resolvedProjectHome, androidSdk)
    }

    testContext.runIDE(commands = CommandChain().exitApp())
  }
}