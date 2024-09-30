package com.intellij.tools.plugin.checker.aws

enum class VerificationResultType {
    OK,
    WARNINGS,
    PROBLEMS,
    INVALID_PLUGIN,
    NON_DOWNLOADABLE,
    UNCHANGED;
}
