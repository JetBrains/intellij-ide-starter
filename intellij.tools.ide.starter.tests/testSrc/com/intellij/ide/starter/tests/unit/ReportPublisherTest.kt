package com.intellij.ide.starter.tests.unit

import com.intellij.ide.starter.ci.NoCIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.ide.command.CommandChain
import com.intellij.ide.starter.junit5.JUnit5StarterAssistant
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.report.publisher.ReportPublisher
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.tests.examples.data.TestCases
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.kodein.di.direct
import org.kodein.di.instance
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
@ExtendWith(JUnit5StarterAssistant::class)
class ReportPublisherTest {

  @Mock
  lateinit var ideDataPaths: IDEDataPaths

  @Mock
  lateinit var installedIDE: InstalledIde

  @Mock
  lateinit var ideStartResult: IDEStartResult

  @Mock
  lateinit var context: IDETestContext
  private val commandChain = CommandChain()
  private val patchVMOptions: VMOptions.() -> VMOptions = { this }

  private lateinit var publishers: List<ReportPublisher>
  private lateinit var publisherSpy: ReportPublisher
  private lateinit var ideTestContext: IDETestContext

  @BeforeEach
  fun before() {
    //TODO(Find a way to mock on stage of init of di, due to it little bit dirty approach to put them directly in IDETestContext publishers)
    publishers = di.direct.instance()
    publisherSpy = spy(publishers[0])
    ideTestContext = IDETestContext(
      ideDataPaths,
      installedIDE,
      TestCases.IC.GradleJitPackSimple,
      "Test method",
      null,
      patchVMOptions = patchVMOptions,
      NoCIServer,
      publishers = listOf(publisherSpy),
      isReportPublishingEnabled = true
    )
  }

  @Test
  fun `test that report publishers were invoked successfully`() {
    //GIVEN
    val ideRunContext = IDERunContext(ideTestContext)
    val spyIdeRunContext = spy(ideRunContext)
    val spyTestContext = spy(ideTestContext)

    //WHEN
    doReturn(spyIdeRunContext).`when`(spyTestContext).runContext(commands = commandChain, patchVMOptions = patchVMOptions)
    doReturn(ideStartResult).`when`(spyIdeRunContext).runIDE()
    doReturn(context).`when`(spyIdeRunContext).testContext

    //THEN
    spyTestContext.runIDE(commands = commandChain, patchVMOptions = patchVMOptions)
    //ASSERT
    verify(publisherSpy, times(1)).publishResult(ideStartResult)
    verify(publisherSpy, times(1)).publishAnyway(context)
  }

  @Test
  fun `test that report publisher were fail due exception and only publishAnyway report`() {
    //GIVEN
    val ideRunContext = IDERunContext(ideTestContext)
    val spyIdeRunContext = spy(ideRunContext)
    val spyTestContext = spy(ideTestContext)

    //WHEN
    doReturn(spyIdeRunContext).`when`(spyTestContext).runContext(commands = commandChain, patchVMOptions = patchVMOptions)
    doThrow(IllegalArgumentException::class.java).`when`(spyIdeRunContext).runIDE()
    doReturn(context).`when`(spyIdeRunContext).testContext
    //THEN
    Assertions.assertThrows(IllegalArgumentException::class.java) {
      spyTestContext.runIDE(commands = commandChain, patchVMOptions = patchVMOptions)
    }
    //ASSERT
    verify(publisherSpy, times(0)).publishResult(ideStartResult)
    verify(publisherSpy, times(1)).publishAnyway(context)
  }
}