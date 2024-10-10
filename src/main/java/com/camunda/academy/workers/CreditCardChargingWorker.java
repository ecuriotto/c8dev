package com.camunda.academy.workers;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.annotation.JobWorker;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.camunda.academy.exceptions.InvalidCreditCardException;
import com.camunda.academy.services.CreditCardService;

@Component
public class CreditCardChargingWorker {

  Logger LOGGER = LoggerFactory.getLogger(CreditCardChargingWorker.class);

  @Autowired CreditCardService creditCardService;

  @JobWorker(type = "credit-card-charging", autoComplete=false)
  public void handleCreditCardCharging(final JobClient jobClient, final ActivatedJob job) {
    LOGGER.info("Task definition type: " + job.getType());
    Map<String, Object> variables = job.getVariablesAsMap();
    String cardNumber = variables.get("cardNumber").toString();
    String cvc = variables.get("cvc").toString();
    String expiryDate = variables.get("expiryDate").toString();
    Double amount = Double.valueOf(variables.get("openAmount").toString());

    try {
        creditCardService.chargeAmount(cardNumber, cvc, expiryDate, amount);
        
        jobClient.newCompleteCommand(job)          
        .send()
        .exceptionally((
            throwable -> {
            throw new RuntimeException("Could not complete job", throwable);
            }
        ));
    }
    catch(InvalidCreditCardException ie){
        jobClient.newThrowErrorCommand(job).errorCode("creditCardChargeError")
        .variables(Map.of("myErrorMessage", "Expiry date with wrong number of digits"))
        //.errorMessage("This is ane error with the date of the credit card")//This is not handled by Zeebe at the moment
        .send()
        .exceptionally((
            throwable -> {
            throw new RuntimeException("Could not complete job", throwable);
            }
        ));
    }
    catch(Exception e){
      Integer retries = job.getRetries();
      jobClient.newFailCommand(job).retries(retries -1).errorMessage(e.getMessage())
              .send()
        .exceptionally((
            throwable -> {
            throw new RuntimeException("Could not complete job", throwable);
            }
        ));
    }

  }
}
