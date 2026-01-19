package com.camunda.academy.workers;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.annotation.JobWorker;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.camunda.academy.services.CustomerService;

@Component
public class CreditDeductionCompensationWorker {

  @Autowired
  CustomerService customerService;
  Logger LOGGER = LoggerFactory.getLogger(CreditDeductionCompensationWorker.class);

  @JobWorker(type = "compensate-credit-deduction")
  public void handleCreditCardCharging(final JobClient jobClient, final ActivatedJob job) {
    LOGGER.info("Task definition type: " + job.getType());

    Map<String, Object> variables = job.getVariablesAsMap();
    Double openAmount = Double.valueOf(variables.get("openAmount").toString());
    Double orderTotal = Double.valueOf(variables.get("orderTotal").toString());

    String message = "Amount to be compensated: " + openAmount + " for order total: " + orderTotal;
    LOGGER.info(message);
    Boolean compensate = true;

    jobClient.newCompleteCommand(job).variables(Map.of("compensate", compensate)).send().join();

  }
}
