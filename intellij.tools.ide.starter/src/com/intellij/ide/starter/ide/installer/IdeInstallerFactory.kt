package com.intellij.ide.starter.ide.installer

import com.intellij.ide.starter.ide.IdeInstallator
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.IdeInfo

open class IdeInstallerFactory {
  open fun createInstaller(ideInfo: IdeInfo): IdeInstallator {
    return if (ideInfo.productCode == IdeProductProvider.AI.productCode)
      AndroidInstaller()
    else
      SimpleInstaller()
  }
}