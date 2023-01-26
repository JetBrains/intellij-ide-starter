### Tests that perform smoke validation of non-JetBrains plugins

#### How to debug

Tests are downloading IDE's and plugins and then install the plugin in an IDE and execute simple actions: import project, etc.  
Parameters what IDE and plugin to download are specified in TeamCity trigger info.  
Initially those parameters are sent from Marketplace Team (AWS EventBus -> SNS topic -> TeamCity trigger)  


Use properties (search by files in project)
```
Pair("teamcity.build.id", "BUILD_ID"),
Pair("teamcity.auth.userId", "YOUR_USER_ID"),
Pair("teamcity.auth.password", "YOUR_SECRET" )
```
They will authorize you on TC server during local run and use build trigger info as an input data.