package com.intellij.ide.starter.junit5.events

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.junit5.config.KillOutdatedProcesses
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.runner.TestContainer
import com.intellij.ide.starter.runner.TestContextInitializedEvent
import com.intellij.ide.starter.utils.hyphenateTestName
import com.intellij.tools.ide.starter.bus.StarterBus
import io.kotest.assertions.timing.eventually
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@ExtendWith(MockitoExtension::class)
@ExtendWith(KillOutdatedProcesses::class)
class TestContextInitializationEventsTest {
  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private lateinit var testCase: TestCase<*>

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private lateinit var installedIde: InstalledIde

  private val container = object : TestContainer<Any> {
    override val setupHooks: MutableList<IDETestContext.() -> IDETestContext> = mutableListOf()

    override suspend fun resolveIDE(ideInfo: IdeInfo): Pair<String, InstalledIde> = Pair("1000.200.30", installedIde)

    override fun installPerformanceTestingPluginIfMissing(context: IDETestContext) {}
  }

  @AfterEach
  fun afterEach() {
    StarterBus.unsubscribeAll()
  }

  @RepeatedTest(value = 200)
  fun `events for test runner init should be fired`(testInfo: TestInfo) {
    val firedEvents = mutableListOf<TestContextInitializedEvent>()
    StarterBus.subscribe(this) { event: TestContextInitializedEvent -> firedEvents.add(event) }

    val testName = testInfo.displayName.hyphenateTestName()

    container.newContext(testName = testName, testCase = testCase)

    runBlocking {
      eventually(duration = 2.seconds, poll = 100.milliseconds) {
        firedEvents.shouldHaveSize(1)
      }
    }
  }
}