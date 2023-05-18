package com.intellij.ide.starter.models

object IdeProductImp : IdeProduct {
  /** GoLand */
  override val GO = IdeInfo(
    productCode = "GO",
    platformPrefix = "GoLand",
    executableFileName = "goland",
    fullName = "GoLand"
  )

  /** IntelliJ Ultimate */
  override val IU = IdeInfo(
    productCode = "IU",
    platformPrefix = "idea",
    executableFileName = "idea",
    fullName = "IDEA"
  )

  /** IntelliJ Community */
  override val IC = IdeInfo(
    productCode = "IC",
    platformPrefix = "Idea",
    executableFileName = "idea",
    fullName = "IDEA Community"
  )

  /** Android Studio */
  override val AI = IdeInfo(
    productCode = "AI",
    platformPrefix = "AndroidStudio",
    executableFileName = "studio",
    fullName = "Android Studio"
  )

  /** WebStorm */
  override val WS = IdeInfo(
    productCode = "WS",
    platformPrefix = "WebStorm",
    executableFileName = "webstorm",
    fullName = "WebStorm"
  )

  /** PhpStorm */
  override val PS = IdeInfo(
    productCode = "PS",
    platformPrefix = "PhpStorm",
    executableFileName = "phpstorm",
    fullName = "PhpStorm"
  )

  /** DataGrip */
  override val DB = IdeInfo(
    productCode = "DB",
    platformPrefix = "DataGrip",
    executableFileName = "datagrip",
    fullName = "DataGrip"
  )

  /** RubyMine */
  override val RM = IdeInfo(
    productCode = "RM",
    platformPrefix = "Ruby",
    executableFileName = "rubymine",
    fullName = "RubyMine"
  )

  /** PyCharm Professional */
  override val PY = IdeInfo(
    productCode = "PY",
    platformPrefix = "Python",
    executableFileName = "pycharm",
    fullName = "PyCharm"
  )

  /** CLion */
  override val CL: IdeInfo = IdeInfo(
    productCode = "CL",
    platformPrefix = "CLion",
    executableFileName = "clion",
    fullName = "CLion"
  )

  /** DataSpell */
  override val DS: IdeInfo = IdeInfo(
    productCode = "DS",
    platformPrefix = "DataSpell",
    executableFileName = "dataspell",
    fullName = "DataSpell"
  )

  /** PyCharm Community */
  override val PC: IdeInfo = IdeInfo(
    productCode = "PC",
    platformPrefix = "PyCharmCore",
    executableFileName = "pycharm",
    fullName = "PyCharm"
  )

  /** Aqua */
  override val QA: IdeInfo = IdeInfo(
    productCode = "QA",
    platformPrefix = "Aqua",
    executableFileName = "aqua",
    fullName = "Aqua"
  )
}