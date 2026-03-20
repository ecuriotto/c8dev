package com.camunda.academy;

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.assertions.ElementSelectors.byName;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byKey;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.Deployment;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(classes = PaymentProcessWithManualJobsTest.TestProcessApplication.class, properties = {
                "camunda.client.worker.defaults.enabled=false" })
@CamundaSpringProcessTest
// Reset the Spring context after each test method.
// This keeps process engine state isolated so one test cannot influence
// another.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PaymentProcessWithManualJobsTest {

        @Autowired
        private CamundaClient client;

        @Autowired
        private CamundaProcessTestContext processTestContext;

        // This class demonstrates a "manual jobs" testing style:
        // workers are disabled and tests move the process forward by explicitly
        // completing jobs.

        @Test
        void shouldStopAtDeductCredit() {
                long processInstanceKey = startPaymentProcess(baseVariables());

                assertThatProcessInstance(byKey(processInstanceKey))
                                .hasActiveElements(byName("Deduct credit"));

                processTestContext.completeJob("credit-deduction", Map.of("openAmount", 0.0));
                processTestContext.completeJob("payment-completion");

                assertThatProcessInstance(byKey(processInstanceKey)).isCompleted();
        }

        @Test
        void shouldCompleteWhenCustomerCreditIsEnough() {
                long processInstanceKey = startPaymentProcess(baseVariables());

                processTestContext.completeJob("credit-deduction", Map.of("openAmount", 0.0));

                assertThatProcessInstance(byKey(processInstanceKey))
                                .hasActiveElements(byName("Complete payment"));

                processTestContext.completeJob("payment-completion");

                assertThatProcessInstance(byKey(processInstanceKey))
                                .hasCompletedElementsInOrder(
                                                byName("Payment required"),
                                                byName("Deduct credit"),
                                                byName("Credit sufficient?"),
                                                byName("Complete payment"),
                                                byName("Payment completed"))
                                .isCompleted();
        }

        @Test
        void shouldCompleteThroughCreditCardChargingPath() {
                long processInstanceKey = startPaymentProcess(baseVariables());

                processTestContext.completeJob("credit-deduction", Map.of("openAmount", 50.0));
                processTestContext.completeJob("credit-card-charging");

                assertThatProcessInstance(byKey(processInstanceKey))
                                .hasActiveElements(byName("Complete payment"));

                processTestContext.completeJob("payment-completion");

                assertThatProcessInstance(byKey(processInstanceKey))
                                .hasCompletedElementsInOrder(
                                                byName("Payment required"),
                                                byName("Deduct credit"),
                                                byName("Credit sufficient?"),
                                                byName("Charge credit card"),
                                                byName("Complete payment"),
                                                byName("Payment completed"))
                                .isCompleted();
        }

        @Test
        void shouldReachHandleErrorPathWhenChargingThrowsBpmnError() {
                processTestContext
                                .mockJobWorker("credit-card-charging")
                                .withHandler((jobClient, job) -> jobClient
                                                .newThrowErrorCommand(job)
                                                .errorCode("creditCardChargeError")
                                                .variables(Map.of("myErrorMessage",
                                                                "Expiry date with wrong number of digits"))
                                                .send()
                                                .join());

                long processInstanceKey = startPaymentProcess(baseVariables());

                processTestContext.completeJob("credit-deduction", Map.of("openAmount", 50.0));

                assertThatProcessInstance(byKey(processInstanceKey))
                                .hasActiveElements(byName("Handle error"));
        }

        @Test
        void shouldPauseAtHandleErrorUntilManualDecision() {
                processTestContext
                                .mockJobWorker("credit-card-charging")
                                .withHandler((jobClient, job) -> jobClient
                                                .newThrowErrorCommand(job)
                                                .errorCode("creditCardChargeError")
                                                .variables(Map.of("myErrorMessage",
                                                                "Expiry date with wrong number of digits"))
                                                .send()
                                                .join());

                long processInstanceKey = startPaymentProcess(baseVariables());

                processTestContext.completeJob("credit-deduction", Map.of("openAmount", 50.0));

                assertThatProcessInstance(byKey(processInstanceKey))
                                .isActive()
                                .hasActiveElements(byName("Handle error"));
        }

        @Test
        @Disabled("CPT test runtime does not expose Handle error form task as a completable user task")
        void shouldCompleteThroughFailPaymentWhenErrorIsNotResolved() {
                processTestContext
                                .mockJobWorker("credit-card-charging")
                                .withHandler((jobClient, job) -> jobClient
                                                .newThrowErrorCommand(job)
                                                .errorCode("creditCardChargeError")
                                                .variables(Map.of("myErrorMessage",
                                                                "Expiry date with wrong number of digits"))
                                                .send()
                                                .join());

                long processInstanceKey = startPaymentProcess(baseVariables());

                processTestContext.completeJob("credit-deduction", Map.of("openAmount", 50.0));

                assertThatProcessInstance(byKey(processInstanceKey))
                                .hasActiveElements(byName("Handle error"));

                // Simulate user decision: error not resolved — use client API to complete the
                // Handle error task
                // with errorResolved=false, which triggers the fail-payment branch.
                processTestContext.completeJob("io.camunda.zeebe:userTask", Map.of("errorResolved", false));

                processTestContext.completeJob("payment-completion");

                assertThatProcessInstance(byKey(processInstanceKey))
                                .hasCompletedElementsInOrder(
                                                byName("Payment required"),
                                                byName("Deduct credit"),
                                                byName("Credit sufficient?"),
                                                byName("Charge credit card"),
                                                byName("Handle error"),
                                                byName("Fail payment"))
                                .isCompleted();
        }

        private long startPaymentProcess(Map<String, Object> inputVariables) {
                Map<String, Object> variables = new HashMap<>(inputVariables);
                variables.put("orderId", "ORDER-" + UUID.randomUUID());

                client.newPublishMessageCommand()
                                .messageName("paymentRequestMessage")
                                .correlationKey(variables.get("orderId").toString())
                                .variables(variables)
                                .send()
                                .join();

                return waitForProcessInstanceKey();
        }

        // Poll for the active PaymentProcess instance created by the message start
        // event.
        private long waitForProcessInstanceKey() {
                Instant deadline = Instant.now().plus(Duration.ofSeconds(15));

                while (Instant.now().isBefore(deadline)) {
                        var instances = client.newProcessInstanceSearchRequest()
                                        .filter(filter -> filter
                                                        .state(ProcessInstanceState.ACTIVE)
                                                        .processDefinitionId("PaymentProcess"))
                                        .send()
                                        .join()
                                        .items();

                        if (!instances.isEmpty()) {
                                return instances.get(0).getProcessInstanceKey();
                        }
                }

                throw new AssertionError("Could not find active PaymentProcess instance");
        }

        private Map<String, Object> baseVariables() {
                return Map.of(
                                "customerId", "customer100",
                                "orderTotal", 50.0,
                                "cardNumber", "4111111111111111",
                                "cvc", "123",
                                "expiryDate", "12/30");
        }

        @SpringBootApplication(scanBasePackages = "com.camunda.academy")
        // Deploy both process models because payment completion can publish messages
        // back to order flow.
        @Deployment(resources = { "classpath:/Order Process.bpmn", "classpath:/Payment Process.bpmn" })
        static class TestProcessApplication {
        }
}
