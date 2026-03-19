package com.camunda.academy.connector;

import jakarta.validation.constraints.NotEmpty;

public class ProductCatalogInput {

    @NotEmpty
    private String productId;

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }
}
