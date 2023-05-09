### Starter Core

#### Overview

This repository contains the core of the Starter test framework for IntelliJ IDEA-based IDEs. For a general overview, refer to the [main README](https://github.com/JetBrains/intellij-ide-starter/README.md)

##### Run with JUnit5

[Example of test based on JUnit5](https://github.com/JetBrains/intellij-ide-starter/blob/master/intellij.tools.ide.starter.examples/testSrc/com/intellij/ide/starter/examples/junit5/IdeaJUnit5ExampleTest.kt)

##### Run with JUnit4

We recommend using JUnit 5 whenever possible, as JUnit 4 is still supported but no longer under active development.
[Example of test based on JUnit 4](https://github.com/JetBrains/intellij-ide-starter/blob/master/intellij.tools.ide.starter.examples/testSrc/com/intellij/ide/starter/examples/junit4/IdeaJUnit4ExampleTests.kt)

#### What behaviour might be extended / modified

You can modify or extend any behavior initialized through the Kodein DI framework according to your needs. To do so, refer to the    
[DI container initialization](https://github.com/JetBrains/intellij-ide-starter/blob/master/intellij.tools.ide.starter/src/com/intellij/ide/starter/di/diContainer.kt)  
For example, you can create your own implementation of CIServer and provide it through DI. Make sure to use the same Kodein version specified in the starter project's `build.gradle`.

Example:

```
di = DI {
      extend(di)
      bindSingleton<CIServer>(overrides = true) { YourImplementationOfCI() }
}
```

### Debugging the test

Since tests are executed inside the IDE as an external process for test, you cannot directly debug them. 
To debug a test, you need to connect remotely to the IDE instance.

General debugging workflow:

1. Add the following call on `context` object
```
...

context
.addVMOptionsPatch { debug() }

...
```
2. Create run configuration for Remote JVM Debug:
Debugger mode: **Attach to Remote JVM**   
Host: **localhost** Port: **5005**  
Command line arguments for remote JVM: ```-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005```  
3. Run your test. 

After seeing the console prompt to connect remotely to port 5005, run the created run configuration.


### Using Tweaks to Modify Starter Behavior

For JUnit5, there are several extensions with the Tweak prefix, which provide a convenient way to set configuration variables as needed.

Example:
```
@ExtendWith(TweakEnableClassFileVerification::class)
@ExtendWith(TweakUseLatestDownloadedIdeBuild::class)
class ClassWithTest {
...
}
```

The configuration storage is `com.intellij.ide.starter.config.StarterConfigurationStorage`

### Downloading custom releases
Downloading Custom Releases

By default, when useEAP or useRelease methods are called, IDE installers will be downloaded from JetBrains' public hosting. If no version is specified, the latest version will be used. However, you can specify a desired version if needed.  

### Modifying VM Options

There are two ways to modify the VM options. One is on `IDETestContext` and another on `IDERunContext`. The first one is used to modify
VM options for the whole context that can be reused between runs. The second is used to modify VM options just for the current run.