package com.intellij.tools.plugin.checker.tests

import com.intellij.ide.starter.models.IdeInfo

/**
 * Temporary solution until https://jetbrains.team/p/ij/reviews/200906/timeline
 */
object IdeProductImpl {
  /** GoLand */
  val GO = IdeInfo(
    productCode = "GO",
    platformPrefix = "GoLand",
    executableFileName = "goland",
    fullName = "GoLand",
    qodanaProductCode = "QDGO"
  )

  /** IntelliJ IDEA */
  val IU = IdeInfo(
    productCode = "IU",
    platformPrefix = "idea",
    executableFileName = "idea",
    fullName = "IDEA",
    qodanaProductCode = "QDJVM"
  )

  /** IntelliJ Community */
  val IC = IdeInfo(
    productCode = "IC",
    platformPrefix = "Idea",
    executableFileName = "idea",
    fullName = "IDEA Community",
    qodanaProductCode = "QDJVMC"
  )

  /** Android Studio */
  val AI = IdeInfo(
    productCode = "AI",
    platformPrefix = "AndroidStudio",
    executableFileName = "studio",
    fullName = "Android Studio"
  )

  /** WebStorm */
  val WS = IdeInfo(
    productCode = "WS",
    platformPrefix = "WebStorm",
    executableFileName = "webstorm",
    fullName = "WebStorm",
    qodanaProductCode = "QDJS"
  )

  /** PhpStorm */
  val PS = IdeInfo(
    productCode = "PS",
    platformPrefix = "PhpStorm",
    executableFileName = "phpstorm",
    fullName = "PhpStorm",
    qodanaProductCode = "QDPHP"
  )

  /** DataGrip */
  val DB = IdeInfo(
    productCode = "DB",
    platformPrefix = "DataGrip",
    executableFileName = "datagrip",
    fullName = "DataGrip"
  )

  /** RubyMine */
  val RM = IdeInfo(
    productCode = "RM",
    platformPrefix = "Ruby",
    executableFileName = "rubymine",
    fullName = "RubyMine"
  )

  /** PyCharm */
  val PY = IdeInfo(
    productCode = "PY",
    platformPrefix = "Python",
    executableFileName = "pycharm",
    fullName = "PyCharm",
    qodanaProductCode = "QDPY"
  )

  /** CLion */
  val CL: IdeInfo = IdeInfo(
    productCode = "CL",
    platformPrefix = "CLion",
    executableFileName = "clion",
    fullName = "CLion",
    qodanaProductCode = "QDCPP"
  )

  /** DataSpell */
  val DS: IdeInfo = IdeInfo(
    productCode = "DS",
    platformPrefix = "DataSpell",
    executableFileName = "dataspell",
    fullName = "DataSpell"
  )

  /** PyCharm Community */
  val PC: IdeInfo = IdeInfo(
    productCode = "PC",
    platformPrefix = "PyCharmCore",
    executableFileName = "pycharm",
    fullName = "PyCharm",
    qodanaProductCode = "QDPYC"
  )

  /** Aqua */
  val QA: IdeInfo = IdeInfo(
    productCode = "QA",
    platformPrefix = "Aqua",
    executableFileName = "aqua",
    fullName = "Aqua"
  )

  /** RustRover */
  val RR: IdeInfo = IdeInfo(
    productCode = "RR",
    platformPrefix = "RustRover",
    executableFileName = "rustrover",
    fullName = "RustRover",
    qodanaProductCode = "QDRST"
  )

  /** Rider */
  val RD = IdeInfo(
    productCode = "RD",
    platformPrefix = "Rider",
    executableFileName = "rider",
    fullName = "Rider",
    qodanaProductCode = "QDNET"
  )

  /** Gateway */
  val GW = IdeInfo(
    productCode = "GW",
    platformPrefix = "Gateway",
    executableFileName = "gateway",
    fullName = "Gateway"
  )

  val allId = setOf(GO, IU, IC, AI, WS, PS, DB, RM, PY, CL, DS, PC, RR, QA, QA, RR, RD, GW)
}