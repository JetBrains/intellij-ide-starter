package com.intellij.ide.starter.ide

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import org.kodein.di.direct
import org.kodein.di.instance

interface IdeInstaller {
  val downloader: IdeDownloader
    get() = di.direct.instance<IdeDownloader>()

  /**
   * @param includeRuntimeModuleRepository if `true`, installed IDE will include [runtime module repository](psi_element://com.intellij.platform.runtime.repository);
   * note that production builds always include it, so this parameter is taken into account only when IDE is built from sources.
   * @return <Build Number, InstalledIde>
   */
  fun install(ideInfo: IdeInfo, includeRuntimeModuleRepository: Boolean = false): Pair<String, InstalledIde>
}