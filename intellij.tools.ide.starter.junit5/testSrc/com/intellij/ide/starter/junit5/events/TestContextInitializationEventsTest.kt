package com.intellij.ide.starter.junit5.events


import com.intellij.ide.starter.bus.EventState
import com.intellij.ide.starter.bus.StarterBus
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.junit5.KillOutdatedProcesses
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.runner.TestContainer
import com.intellij.ide.starter.runner.TestContextInitializedEvent
import com.intellij.ide.starter.utils.hyphenateTestName
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.timing.eventually
import io.kotest.assertions.withClue
import io.kotest.inspectors.shouldForAtLeastOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
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

    override fun resolveIDE(ideInfo: IdeInfo): Pair<String, InstalledIde> = Pair("1000.200.30", installedIde)
    override fun installPerformanceTestingPluginIfMissing(context: IDETestContext) {}
  }

  @AfterEach
  fun afterEach() {
    StarterBus.LISTENER.unsubscribe()
  }

  @RepeatedTest(value = 200)
  fun `events for test runner init should be fired`(testInfo: TestInfo) {
    val firedEvents = mutableListOf<TestContextInitializedEvent>()
    StarterBus.subscribe(this) { event: TestContextInitializedEvent -> firedEvents.add(event) }

    val testName = testInfo.displayName.hyphenateTestName()

    container.newContext(testName = testName, testCase = testCase)

    runBlocking {
      eventually(duration = 2.seconds, poll = 100.milliseconds) {
        withClue("There should be 1 events fired. Events: ${firedEvents.map { it.state }}") {
          firedEvents.shouldHaveSize(1)
        }
      }
    }

    assertSoftly {
      withClue("Event should be fired at the end of test context initialization: Events: ${firedEvents.map { it.state }}") {
        firedEvents.shouldForAtLeastOne { it.state.shouldBe(EventState.AFTER) }
      }
    }
  }
}