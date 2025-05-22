package com.intellij.tools.plugin.checker.aws

enum class VerificationResultType {
    OK,
    UNABLE_TO_VERIFY,
    WARNINGS,
    PROBLEMS,
    INVALID_PLUGIN,
    NON_DOWNLOADABLE,
    UNCHANGED;
}
