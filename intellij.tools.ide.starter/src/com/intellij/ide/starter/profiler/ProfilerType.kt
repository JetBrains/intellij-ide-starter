package com.intellij.ide.starter.profiler

enum class ProfilerType(val kind: String) {
  @Deprecated("Use Async Profiler since YourKit can't be used on nightly versions and in dev server", replaceWith = ReplaceWith("ASYNC"))
  YOURKIT("YOURKIT"),
  ASYNC("ASYNC"),
  NONE("NONE");
}
