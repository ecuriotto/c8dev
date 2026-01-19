package com.camunda.academy.workers;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.annotation.JobWorker;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SendMessageWorker {

  Logger LOGGER = LoggerFactory.getLogger(SendMessageWorker.class);

  @Autowired
  private CamundaClient camundaClient;

  @JobWorker(type = "payment-invocation", autoComplete = false)
  public void handlePaymentInvocation(final JobClient jobClient, final ActivatedJob job) {
    LOGGER.info("Task definition type: " + job.getType());

    Map<String, Object> variables = job.getVariablesAsMap();
    String orderId = generateRandomOrderId(6);
    variables.put("orderId", orderId);

    camundaClient.newPublishMessageCommand()
        .messageName("paymentRequestMessage")
        .correlationKey(orderId)
        .variables(variables)
        .send().join();

    jobClient.newCompleteCommand(job).variables(variables).send().join();
  }

  @JobWorker(type = "payment-completion", autoComplete = false)
  public void handlePaymentCompletion(final JobClient jobClient, final ActivatedJob job) {
    LOGGER.info("Task definition type: " + job.getType());

    Map<String, Object> variables = job.getVariablesAsMap();

    camundaClient.newPublishMessageCommand()
        .messageName("paymentCompletedMessage")
        .correlationKey(variables.get("orderId").toString())
        .send().join();

    jobClient.newCompleteCommand(job)
        .send()
        .exceptionally((throwable -> {
          throw new RuntimeException("Could not complete job", throwable);
        }));
  }

  @JobWorker(type = "payment-failure", autoComplete = false)
  public void handlePaymentFailure(final JobClient jobClient, final ActivatedJob job) {
    LOGGER.info("Task definition type: " + job.getType());

    Map<String, Object> variables = job.getVariablesAsMap();

    camundaClient.newPublishMessageCommand()
        .messageName("paymentFailedMessage")
        .correlationKey(variables.get("orderId").toString())
        .send().join();

    jobClient.newCompleteCommand(job)
        .send()
        .exceptionally((throwable -> {
          throw new RuntimeException("Could not complete job", throwable);
        }));
  }

  // Generates a random order ID with a given length, consisting of letters and/or
  // digits
  private String generateRandomOrderId(int length) {
    var stringBuilder = new StringBuilder();
    var random = new Random();

    for (int i = 0; i < length; ++i) {
      boolean appendChar = random.nextBoolean();

      if (appendChar) {
        stringBuilder.append((char) ('A' + random.nextInt(26)));
      } else {
        stringBuilder.append(random.nextInt(9));
      }
    }

    return stringBuilder.toString();
  }
}
