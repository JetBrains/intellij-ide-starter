package com.intellij.tools.plugin.checker.tests

import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.tools.plugin.checker.marketplace.MarketplaceEvent

data class EventToTestCaseParams(val event: MarketplaceEvent, val testCase: TestCase) {
  fun onIDE(ideInfo: IdeInfo) = copy(testCase = testCase.onIDE(ideInfo))
}
