package com.camunda.academy;

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.assertions.ElementSelectors.byName;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byKey;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.camunda.academy.services.CreditCardService;
import com.camunda.academy.services.CustomerService;
import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.Deployment;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(classes = OrderProcessWithWorkersTest.TestProcessApplication.class)
@CamundaSpringProcessTest
// Reset the Spring context after each test so workers, subscriptions, and mocks
// start clean.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderProcessWithWorkersTest {

    @Autowired
    private CamundaClient client;

    // Replace the real service bean with a Mockito mock in the Spring test context.
    @MockitoBean
    private CustomerService customerService;

    // Replace the real service bean with a Mockito mock in the Spring test context.
    @MockitoBean
    private CreditCardService creditCardService;

    // This class uses the "real workers + mocked services" style:
    // order and payment workers run as in production, but service outcomes are
    // controlled by stubs.

    @Test
    void shouldCompleteOrderProcessWithWorkersEnabled() {
        when(customerService.deductCredit(anyString(), anyDouble())).thenReturn(0.0);

        long processInstanceKey = startOrderProcess(baseVariables());

        assertThatProcessInstance(byKey(processInstanceKey))
                .hasCompletedElementsInOrder(
                        byName("Order received"),
                        byName("Invoke Payment"),
                        byName("Payment completed"),
                        byName("Order completed"))
                .isCompleted();
    }

    @Test
    void shouldCompleteOrderProcessThroughCreditCardPathWithWorkersEnabled() {
        when(customerService.deductCredit(anyString(), anyDouble())).thenReturn(50.0);

        long processInstanceKey = startOrderProcess(baseVariables());

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
    // Deploy both models since OrderProcess delegates to PaymentProcess and
    // continues on message correlation.
    @Deployment(resources = { "classpath:/Order Process.bpmn", "classpath:/Payment Process.bpmn" })
    static class TestProcessApplication {
    }
}
