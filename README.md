[![JetBrains incubator project](https://jb.gg/badges/incubator.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub) 
![](https://camo.githubusercontent.com/b044da88664180ea9ad36112161507223610b3bd229f10a67e47145edf94a8f5/68747470733a2f2f6a622e67672f6261646765732f6f6666696369616c2d706c61737469632e737667)
![](https://github.com/JetBrains/intellij-ide-starter/actions/workflows/starter-examples.yaml/badge.svg)

### Starter for IntelliJ IDEA based IDE's

#### Overview

The Starter for IntelliJ IDEA based IDEs is designed to help you create and run tests for IntelliJ-based IDEs. 
It can run an IDE from an existing installer or download one if needed, configure IDE, launch the IDE as an external process, send commands to the IDE, 
and collect the logs, metrics and other IDEs output after test execution.

Additional features include:
* Executing commands inside IDE (see the list of available commands below)
* Implementing custom commands for use in tests
* Executing custom code (without relying on external libraries not available in the IDE)
* CI integration (optional)
* Test artifact collection
* Reporting artifacts to CI (optional)
* Running tests with a profiler (not yet included)

More details how it works can be [found here](https://github.com/JetBrains/intellij-ide-starter/blob/master/intellij.tools.ide.starter/README.md)

#### Supported products

* IDEA
* GoLand
* WebStorm
* PhpStorm
* DataGrip
* PyCharm
* RubyMine
* Android Studio

##### How to use

For the required `build.gradle` and various test scenarios, refer to the [Examples](https://github.com/JetBrains/intellij-ide-starter/tree/master/intellij.tools.ide.starter.examples) directory.

##### Available commands

The available commands can be found in the [performance-testing-commands package](https://github.com/JetBrains/intellij-ide-starter/blob/master/intellij.tools.ide.performanceTesting.commands/src/com/jetbrains/performancePlugin/commands/chain/generalCommandChain.kt)
which includes performance-intensive operations and IDE configuration commands.

Examples of included commands:
- waitForSmartMode()
- flushIndexes()
- setupProjectSdk(sdkName: String, sdkType: String, sdkPath: String)]
- openFile(relativePath: String)
- openProject(projectPath: Path)
- reopenProject()
- goto(line: String, column: String)
- findUsages()
- inspectCode()
- checkOnRedCode()
- exitApp(forceExit: Boolean = true)
- memoryDump()
- dumpProjectFiles()
- compareProjectFiles(firstDir: String, secondDir: String)
- cleanCaches()
- doComplete(times: Int)
- openProjectView()
- pressKey(key: String)
- delayType(command: String)
- doLocalInspection()
- altEnter(intention: String)
- callAltEnter(times: Int, intention: String = "")
- createAllServicesAndExtensions()
- runConfiguration(command: String)
- openFileWithTerminate(relativePath: String, terminateIdeInSeconds: Long)
- searchEverywhere(text: String)
- storeIndices()
- compareIndices()
- recoveryAction(action: RecoveryActionType)
- importMavenProject()
- importGradleProject()
- and many more


### Metrics

Metrics are values collected during test execution, including operation durations, command results (e.g., the number of elements in completion lists), and more. To use them, add the `com.jetbrains.intellij.tools:ide-metrics-collector` dependency.

For more information, see the [README](https://github.com/JetBrains/intellij-community/blob/master/tools/intellij.tools.ide.metrics.collector/README.md) for more information.
