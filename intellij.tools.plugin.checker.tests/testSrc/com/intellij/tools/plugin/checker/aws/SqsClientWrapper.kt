package com.intellij.tools.plugin.checker.aws

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

class SqsClientWrapper(private val queueUrl: String,  region: Region) {
    private val awsAccessKey: String = System.getenv("AWS_ACCESS_KEY_ID")
    private val awsSecretKey: String = System.getenv("AWS_SECRET_ACCESS_KEY")

    private val sqsClient: SqsClient = SqsClient.builder()
        .region(region)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(awsAccessKey, awsSecretKey)))
        .build()

    fun sendMessage(message: VerificationMessage) {
        val mapper = jacksonObjectMapper()
        val messageBody = mapper.writeValueAsString(message)

        val sendMsgRequest = SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody(messageBody)
            .build()

        sqsClient.sendMessage(sendMsgRequest)
    }
}