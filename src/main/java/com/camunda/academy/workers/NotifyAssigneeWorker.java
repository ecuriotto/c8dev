package com.camunda.academy.workers;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;

@Component
public class NotifyAssigneeWorker {
    Logger LOGGER = LoggerFactory.getLogger(NotifyAssigneeWorker.class);

    @JobWorker(type = "notify-assignee", autoComplete = false)
    public void handleCreditCardCharging(final JobClient jobClient, final ActivatedJob job) {
        LOGGER.info("Task definition type: " + job.getType());

        Map<String, Object> variables = job.getVariablesAsMap();
        String approver = variables.get("approver").toString();
        String contractTitle = variables.get("contractTitle").toString();
        String contractValue = variables.get("contractValue").toString();
        // variables.put("customerCredit", customerCredit);
        String message = "Dear " + approver + ", a contract titled '" + contractTitle + "' with value " + contractValue
                + " requires your approval.";
        LOGGER.info("Notifying assignee with message: " + message);
        jobClient.newCompleteCommand(job).send().join();
    }
}