package com.intellij.tools.plugin.checker.aws

/**
 *  https://youtrack.jetbrains.com/articles/MP-A-141165489/External-Services-Protosol#result-from-external-service
*/
data class VerificationMessage(
    val verdict: String,
    val resultType: VerificationResultType,
    val url: String,
    val date: Long,
    val id: Int,
    val verificationType: String,
    val verifierVersion: String?
)
