package com.intellij.tools.plugin.checker.aws

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

class SqsClientWrapper(private val queueUrl: String,  region: Region) {
  private val awsAccessKey: String = System.getenv("AWS_ACCESS_KEY_ID")
                                     ?: throw IllegalArgumentException("AWS_ACCESS_KEY_ID is not set in the environment variables")

  private val awsSecretKey: String = System.getenv("AWS_SECRET_ACCESS_KEY")
                                     ?: throw IllegalArgumentException("AWS_SECRET_ACCESS_KEY is not set in the environment variables")

  private val sqsClient: SqsClient by lazy {
    SqsClient.builder()
      .region(region)
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(awsAccessKey, awsSecretKey)))
      .build()
  }

  private val mapper = jacksonObjectMapper()

  fun sendMessage(message: VerificationMessage) {
    try {
      val messageBody = mapper.writeValueAsString(message)
      val sendMsgRequest = SendMessageRequest.builder()
        .queueUrl(queueUrl)
        .messageBody(messageBody)
        .build()

      sqsClient.sendMessage(sendMsgRequest)
      println("Message sent successfully.")
    } catch (e: Exception) {
      println("Failed to send message: ${e.message}")
      e.printStackTrace()
    }
  }

  fun closeClient() {
    sqsClient.close()
  }
}