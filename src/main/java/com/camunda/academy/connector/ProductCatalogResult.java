package com.camunda.academy.connector;

public class ProductCatalogResult {

    private String productName;
    private String productDescription;
    private double productPrice;

    public ProductCatalogResult(String productName, String productDescription, double productPrice) {
        this.productName = productName;
        this.productDescription = productDescription;
        this.productPrice = productPrice;
    }

    // Add getters for all fields
    public String getProductName() {
        return productName;
    }

    public String getProductDescription() {
        return productDescription;
    }

    public double getProductPrice() {
        return productPrice;
    }

}
