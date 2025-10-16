package com.camunda.academy.workers;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CreditCardChargingWorker {

  Logger LOGGER = LoggerFactory.getLogger(CreditCardChargingWorker.class);

  @JobWorker(type = "credit-card-charging")
  public void handleCreditCardCharging(final JobClient jobClient, final ActivatedJob job) {
    LOGGER.info("Task definition type: " + job.getType());
    jobClient.newCompleteCommand(job).send().join();

  }
}
