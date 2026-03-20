package com.camunda.academy;

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.assertions.ElementSelectors.byName;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byKey;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.Deployment;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(classes = OrderProcessWithManualJobsTest.TestProcessApplication.class, properties = {
        "camunda.client.worker.defaults.enabled=false" })
@CamundaSpringProcessTest
// Reset the Spring context after each test to avoid process-instance/state
// leakage across tests.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderProcessWithManualJobsTest {

    @Autowired
    private CamundaClient client;

    @Autowired
    private CamundaProcessTestContext processTestContext;

    // This class uses the "manual jobs" style:
    // workers are disabled and tests complete jobs explicitly for step-by-step
    // assertions.

    @Test
    void shouldStopAtInvokePayment() {
        long processInstanceKey = startOrderProcess(baseVariables());

        assertThatProcessInstance(byKey(processInstanceKey))
                .hasActiveElements(byName("Invoke Payment"));
    }

    @Test
    void shouldWaitAtPaymentCompletedMessageCatch() {
        long processInstanceKey = startOrderProcess(baseVariables());

        processTestContext.completeJob("payment-invocation");

        assertThatProcessInstance(byKey(processInstanceKey))
                .isActive()
                .hasActiveElements(byName("Payment completed"));
    }

    @Test
    void shouldCompleteAfterPaymentCompletedMessage() {
        Map<String, Object> variables = baseVariables();
        long processInstanceKey = startOrderProcess(variables);

        processTestContext.completeJob("payment-invocation");

        client.newPublishMessageCommand()
                .messageName("paymentCompletedMessage")
                .correlationKey(variables.get("orderId").toString())
                .send()
                .join();

        assertThatProcessInstance(byKey(processInstanceKey))
                .hasCompletedElementsInOrder(
                        byName("Order received"),
                        byName("Invoke Payment"),
                        byName("Payment completed"),
                        byName("Order completed"))
                .isCompleted();
    }

    private long startOrderProcess(Map<String, Object> inputVariables) {
        Map<String, Object> variables = new HashMap<>(inputVariables);

        return client.newCreateInstanceCommand()
                .bpmnProcessId("OrderProcess")
                .latestVersion()
                .variables(variables)
                .send()
                .join()
                .getProcessInstanceKey();
    }

    private Map<String, Object> baseVariables() {
        return Map.of(
                "orderId", "ORDER-" + UUID.randomUUID(),
                "customerId", "customer100",
                "orderTotal", 50.0,
                "cardNumber", "4111111111111111",
                "cvc", "123",
                "expiryDate", "12/30");
    }

    @SpringBootApplication(scanBasePackages = "com.camunda.academy")
    // Deploy both models because the order flow invokes payment and waits for its
    // completion message.
    @Deployment(resources = { "classpath:/Order Process.bpmn", "classpath:/Payment Process.bpmn" })
    static class TestProcessApplication {
    }
}
