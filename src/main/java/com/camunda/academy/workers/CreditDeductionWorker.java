package com.camunda.academy.workers;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.annotation.JobWorker;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.camunda.academy.services.CustomerService;

@Component
public class CreditDeductionWorker {

  @Autowired CustomerService customerService;
  Logger LOGGER = LoggerFactory.getLogger(CreditCardChargingWorker.class);

  @JobWorker(type = "credit-deduction")
  public void handleCreditCardCharging(final JobClient jobClient, final ActivatedJob job) {
    LOGGER.info("Task definition type: " + job.getType());

    Map<String, Object> variables = job.getVariablesAsMap();
    String customerId = variables.get("customerId").toString();
    Double orderTotal = Double.valueOf(variables.get("orderTotal").toString());

    double openAmount = customerService.deductCredit(customerId, orderTotal);

    //variables.put("customerCredit", customerCredit);
    variables.put("openAmount", openAmount);

    jobClient.newCompleteCommand(job).variables(variables).send().join();

    

  }
}
