package com.intellij.tools.plugin.checker.tests

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.treeToValue
import com.intellij.ide.starter.community.model.BuildType
import com.intellij.ide.starter.extended.teamcity.TeamCityCIServer
import com.intellij.ide.starter.extended.teamcity.TeamCityClient
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.ide.command.CommandChain
import com.intellij.ide.starter.junit5.JUnit5StarterAssistant
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.runner.TestContainerImpl
import com.intellij.tools.plugin.checker.data.TestCases
import com.intellij.tools.plugin.checker.di.initDI
import com.intellij.tools.plugin.checker.marketplace.MarketplaceEvent
import com.jetbrains.performancePlugin.commands.chain.exitApp
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.TimeUnit


@ExtendWith(JUnit5StarterAssistant::class)
class InstallPluginTest {

  private lateinit var testInfo: TestInfo
  private lateinit var context: TestContainerImpl

  companion object {
    init {
      initDI()
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

      return listOf(modifyTestCaseForIdeVersion(draftParams))
    }

    fun modifyTestCaseForIdeVersion(params: EventToTestCaseParams): EventToTestCaseParams {
      val ideInfo = IdeProductProvider.getProducts().single { it.productCode == params.event.productCode }

      val paramsWithAppropriateIde = params.onIDE(ideInfo)
      val numericProductVersion = paramsWithAppropriateIde.event.getNumericProductVersion()

      val testCase = when (paramsWithAppropriateIde.event.productType) {
        BuildType.EAP.type -> paramsWithAppropriateIde.testCase.useEAP().withBuildNumber(numericProductVersion)
        BuildType.RELEASE.type -> paramsWithAppropriateIde.testCase.useRelease().withBuildNumber(numericProductVersion)
        else -> TODO("Build type `${paramsWithAppropriateIde.event.productType}` is not supported")
      }

      return paramsWithAppropriateIde.copy(testCase = testCase)
    }
  }

  @ParameterizedTest
  @MethodSource("data")
  @Timeout(value = 15, unit = TimeUnit.MINUTES)
  @Disabled
  fun installPluginTest(params: EventToTestCaseParams) {

    val testContext = context
      .initializeTestContext(testName = testInfo.hyphenateWithClass(), testCase = params.testCase)
      .prepareProjectCleanImport()
      .setSharedIndexesDownload(enable = true)
      .apply {
        pluginConfigurator.setupPluginFromURL(params.event.file)
      }

    testContext.runIDE(commands = CommandChain().exitApp())
  }
}