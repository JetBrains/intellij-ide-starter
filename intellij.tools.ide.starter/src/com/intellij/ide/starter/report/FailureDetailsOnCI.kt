package com.intellij.ide.starter.report

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.utils.replaceSpecialCharactersWithHyphens
import org.kodein.di.direct
import org.kodein.di.instance

interface FailureDetailsOnCI {
  companion object {
    val instance: FailureDetailsOnCI
      get() = di.direct.instance<FailureDetailsOnCI>()

    fun getTestMethodName(): String {
      val method = di.direct.instance<CurrentTestMethod>().get()
      return if (method == null) "" else "${method.declaringClass}.${method}"
    }
  }


  fun getFailureDetails(runContext: IDERunContext): String {
    val testMethodName = getTestMethodName().ifEmpty { runContext.contextName }

    return "Test: $testMethodName" + System.lineSeparator() +
           "You can find logs and other useful info in CI artifacts under the path ${runContext.contextName.replaceSpecialCharactersWithHyphens()}"
  }

  fun getLinkToCIArtifacts(runContext: IDERunContext): String? = null
}