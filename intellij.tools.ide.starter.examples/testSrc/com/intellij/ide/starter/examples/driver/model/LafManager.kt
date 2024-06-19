package com.intellij.ide.starter.examples.driver.model

import com.intellij.driver.client.Remote

@Remote("com.intellij.ide.ui.LafManager")
interface LafManager {
    fun getCurrentUIThemeLookAndFeel(): UiThemeLookAndFeelInfo
}
@Remote("com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo")
interface UiThemeLookAndFeelInfo {
    fun getName(): String
}