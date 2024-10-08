package com.camunda.academy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.camunda.zeebe.client.ZeebeClient;

@SpringBootApplication
public class PaymentApplication {

	@Autowired ZeebeClient zeebeClient;

	public static void main(final String... args) {
		SpringApplication.run(PaymentApplication.class, args);
	}


}
