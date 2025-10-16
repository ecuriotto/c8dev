package com.camunda.academy.workers;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.camunda.academy.services.CreditCardService;

@Component
public class CreditCardChargingWorker {

  Logger LOGGER = LoggerFactory.getLogger(CreditCardChargingWorker.class);

  @Autowired CreditCardService creditCardService;

  @JobWorker(type = "credit-card-charging")
  public void handleCreditCardCharging(final JobClient jobClient, final ActivatedJob job) {
    LOGGER.info("Task definition type: " + job.getType());
    Map<String, Object> variables = job.getVariablesAsMap();
    String cardNumber = variables.get("cardNumber").toString();
    String cvc = variables.get("cvc").toString();
    String expiryDate = variables.get("expiryDate").toString();
    Double amount = Double.valueOf(variables.get("openAmount").toString());

    creditCardService.chargeAmount(cardNumber, cvc, expiryDate, amount);
    
    jobClient.newCompleteCommand(job)          
    .send()
    .exceptionally((
        throwable -> {
        throw new RuntimeException("Could not complete job", throwable);
        }
    ));

  }
}
