package com.camunda.academy.connector;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@OutboundConnector(name = "Product Catalog", inputVariables = {
        "productId" }, type = "io.camunda.academy:product-catalog:1")
public class ProductCatalogFunction implements OutboundConnectorFunction {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductCatalogFunction.class);

    // Internal product catalog - simulates a proprietary data source
    private static final Map<String, ProductCatalogResult> CATALOG = Map.of(
            "1", new ProductCatalogResult("Laptop Pro 15", "High-performance laptop for professionals", 1299.99),
            "2", new ProductCatalogResult("Wireless Mouse", "Ergonomic wireless mouse with long battery life", 29.99),
            "3", new ProductCatalogResult("USB-C Hub", "7-in-1 USB-C hub with HDMI and PD charging", 49.99),
            "4",
            new ProductCatalogResult("Mechanical Keyboard", "Compact mechanical keyboard with RGB backlight", 89.99),
            "5", new ProductCatalogResult("Webcam HD", "1080p webcam with built-in noise-cancelling mic", 59.99));

    @Override
    public Object execute(OutboundConnectorContext context) throws Exception {

        // Bind and validate input variables from the process
        var input = context.bindVariables(ProductCatalogInput.class);

        LOGGER.info("Looking up product with ID: {}", input.getProductId());

        var product = CATALOG.get(input.getProductId());

        if (product == null) {
            throw new ConnectorException(
                    "PRODUCT_NOT_FOUND",
                    "No product found with ID: " + input.getProductId());
        }

        LOGGER.info("Found product: {}", product.getProductName());
        return product;
    }

}
