package com.camunda.academy;

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.assertions.ElementSelectors.byName;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byKey;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.camunda.academy.exceptions.InvalidCreditCardException;
import com.camunda.academy.services.CreditCardService;
import com.camunda.academy.services.CustomerService;
import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.Deployment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import io.camunda.process.test.api.CamundaSpringProcessTest;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(classes = PaymentProcessWithWorkersTest.TestProcessApplication.class)
@CamundaSpringProcessTest
// Reset the Spring context after each test so worker state and mocked beans are
// fresh every time.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PaymentProcessWithWorkersTest {

    @Autowired
    private CamundaClient client;

    // Replace the real service bean with a Mockito mock inside the Spring test
    // context.
    @MockitoBean
    private CustomerService customerService;

    // Replace the real service bean with a Mockito mock inside the Spring test
    // context.
    @MockitoBean
    private CreditCardService creditCardService;

    // This class demonstrates a "real workers + mocked services" style:
    // workers execute normally, but external/service behavior is controlled with
    // Mockito.

    @Test
    void shouldCompleteWhenCustomerCreditIsEnough() {
        when(customerService.deductCredit(anyString(), anyDouble())).thenReturn(0.0);

        long processInstanceKey = startPaymentProcess(baseVariables());

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
    void shouldCompleteThroughCreditCardChargingPath() throws InvalidCreditCardException {
        when(customerService.deductCredit(anyString(), anyDouble())).thenReturn(50.0);
        // creditCardService.chargeAmount is a void method — Mockito does nothing by
        // default

        long processInstanceKey = startPaymentProcess(baseVariables());

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
    void shouldReachHandleErrorWhenChargingThrowsBpmnError() throws InvalidCreditCardException {
        when(customerService.deductCredit(anyString(), anyDouble())).thenReturn(50.0);
        doThrow(new InvalidCreditCardException("Invalid expiry date"))
                .when(creditCardService).chargeAmount(anyString(), anyString(), anyString(), anyDouble());

        long processInstanceKey = startPaymentProcess(baseVariables());

        assertThatProcessInstance(byKey(processInstanceKey))
                .isActive()
                .hasActiveElements(byName("Handle error"));
    }

    @Test
    @Disabled("CPT test runtime does not expose Handle error form task as a completable user task")
    void shouldCompleteThroughFailPaymentWhenErrorIsNotResolved() throws InvalidCreditCardException {
        when(customerService.deductCredit(anyString(), anyDouble())).thenReturn(50.0);
        doThrow(new InvalidCreditCardException("Invalid expiry date"))
                .when(creditCardService).chargeAmount(anyString(), anyString(), anyString(), anyDouble());

        long processInstanceKey = startPaymentProcess(baseVariables());

        assertThatProcessInstance(byKey(processInstanceKey))
                .hasActiveElements(byName("Handle error"));

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

    // Poll for the process instance created by the message start event.
    // We do not filter by ACTIVE because with real workers the instance may
    // complete very quickly.
    private long waitForProcessInstanceKey() {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));

        while (Instant.now().isBefore(deadline)) {
            var instances = client.newProcessInstanceSearchRequest()
                    .filter(filter -> filter.processDefinitionId("PaymentProcess"))
                    .send()
                    .join()
                    .items();

            if (!instances.isEmpty()) {
                return instances.get(0).getProcessInstanceKey();
            }
        }

        throw new AssertionError("Could not find PaymentProcess instance");
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
    // Deploy both models: PaymentProcess is under test and OrderProcess is
    // referenced by messaging flow.
    @Deployment(resources = { "classpath:/Order Process.bpmn", "classpath:/Payment Process.bpmn" })
    static class TestProcessApplication {
    }

}
