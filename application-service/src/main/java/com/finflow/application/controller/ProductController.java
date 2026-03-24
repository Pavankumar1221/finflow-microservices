package com.finflow.application.controller;

import com.finflow.application.dto.Product;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/products")
@Tag(name = "Product Controller", description = "Public endpoints for loan products")
public class ProductController {

    @GetMapping
    @Operation(summary = "Get complete list of loan products")
    public List<Product> getAllProducts() {
        return List.of(
            Product.builder()
                .id(1L)
                .name("Home Loan")
                .interestRate(8.5)
                .maxAmount(5000000L)
                .tenure("20 years")
                .build(),
            Product.builder()
                .id(2L)
                .name("Personal Loan")
                .interestRate(12.0)
                .maxAmount(1000000L)
                .tenure("5 years")
                .build(),
            Product.builder()
                .id(3L)
                .name("Car Loan")
                .interestRate(9.5)
                .maxAmount(2500000L)
                .tenure("7 years")
                .build()
        );
    }
}
