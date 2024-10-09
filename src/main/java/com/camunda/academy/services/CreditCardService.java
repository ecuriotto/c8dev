package com.camunda.academy.services;

import org.springframework.stereotype.Component;

import com.camunda.academy.exceptions.InvalidCreditCardException;

@Component
public class CreditCardService {

	public void chargeAmount(String cardNumber, String cvc, String expiryDate, Double amount) throws InvalidCreditCardException {
		
    if (expiryDate.length() == 5) {
      System.out.println(
          "Credit card number: " + cardNumber + ", CVC: " + cvc + ", Expiry date: " + expiryDate
              + ", Order total: " + amount);
    } else {
      System.out.println("The credit card's expiry date is invalid: " + expiryDate);

      throw new InvalidCreditCardException("Invalid credit card expiry date");
    }

	}
}
