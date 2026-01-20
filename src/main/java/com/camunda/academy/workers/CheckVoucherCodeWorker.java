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
public class CheckVoucherCodeWorker {

  @Autowired
  CustomerService customerService;
  Logger LOGGER = LoggerFactory.getLogger(CreditCardChargingWorker.class);

  @JobWorker(type = "check-voucher-code")
  public void handleCreditCardCharging(final JobClient jobClient, final ActivatedJob job) {
    LOGGER.info("Task definition type: " + job.getType());

    Map<String, Object> variables = job.getVariablesAsMap();
    Object voucherCodeObj = variables.get("voucherCode");
    String voucherCode = voucherCodeObj != null ? voucherCodeObj.toString() : null;

    Number discount = 0;

    if (voucherCode != null && !voucherCode.equals("") && !voucherCode.equals("DISCOUNT10")) {
      jobClient.newThrowErrorCommand(job).errorCode("invalidVoucher")
          .send()
          .exceptionally((throwable -> {
            throw new RuntimeException("Could not throw error", throwable);
          }));
      return;
    }

    if (voucherCode != null && voucherCode.equals("DISCOUNT10")) {
      discount = 10;
    }

    // variables.put("customerCredit", customerCredit);
    variables.put("discount", discount);

    jobClient.newCompleteCommand(job).variables(variables).send().join();

  }
}
