package com.intellij.tools.plugin.checker.tests

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.treeToValue
import com.intellij.ide.starter.extended.engine.JBTestContainer
import com.intellij.ide.starter.extended.engine.junit5.JUnit5StarterAssistantExtended
import com.intellij.ide.starter.extended.teamcity.TeamCityCIServer
import com.intellij.ide.starter.extended.teamcity.TeamCityClient
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.ide.command.CommandChain
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.tools.plugin.checker.data.TestCases
import com.intellij.tools.plugin.checker.di.initPluginCheckerDI
import com.intellij.tools.plugin.checker.marketplace.MarketplaceEvent
import com.jetbrains.performancePlugin.commands.chain.exitApp
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.net.URI
import java.util.concurrent.TimeUnit


@ExtendWith(JUnit5StarterAssistantExtended::class)
class InstallPluginTest {

  private lateinit var testInfo: TestInfo
  private lateinit var container: JBTestContainer

  companion object {
    init {
      initPluginCheckerDI()
    }

    @BeforeAll
    @JvmStatic
    fun beforeAll() {
      Assumptions.assumeTrue({ data().isNotEmpty() }, "Cannot run tests, because tests cases are empty")
    }

    /**
     * Extract only sns_message_body from text like this:
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

    fun deserializeMessageFromMarketplace(input: String): MarketplaceEvent {
      val jacksonMapper = jsonMapper {
        addModule(kotlinModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      }

      val detailField = jacksonMapper.readTree(input.extractMarketplaceDetailPayload())
      return jacksonMapper.treeToValue<MarketplaceEvent>(detailField)
    }

    fun getMarketplaceEvent(): MarketplaceEvent {
      val triggeredByJsonNode = TeamCityClient.run {
        get(getGuestAuthUrl().resolve("builds/id:${TeamCityCIServer.buildId}?fields=triggered(displayText)"))
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
        throw RuntimeException("Product ${params.event.productCode} is not supported yet. Link to download it ${params.event.productLink}")
      }

      val ideInfo = IdeProductProvider.getProducts().single { it.productCode == params.event.productCode }
        .copy(downloadURI = URI(params.event.productLink), buildType = params.event.productType ?: "")

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

    testContext.runIDE(commands = CommandChain().exitApp())
  }
}