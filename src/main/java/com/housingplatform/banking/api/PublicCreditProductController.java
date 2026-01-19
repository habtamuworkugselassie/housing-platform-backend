package com.housingplatform.banking.api;

import com.housingplatform.banking.dto.CreditProductResponse;
import com.housingplatform.banking.service.CreditProductService;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/credit-products")
@Tag(name = "Public Credit Products", description = "Public credit product browsing APIs")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
class PublicCreditProductController {

    private final CreditProductService creditProductService;

    @GetMapping
    @Operation(summary = "List active credit products", description = "Retrieve all active credit products from all banks")
    public ResponseEntity<List<CreditProductResponse>> getActiveCreditProducts() {
        List<CreditProductResponse> products = creditProductService.getAllActiveCreditProducts();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get credit product", description = "Retrieve a specific credit product")
    public ResponseEntity<CreditProductResponse> getCreditProduct(@PathVariable UUID productId) {
        CreditProductResponse product = creditProductService.getCreditProductById(productId);
        return ResponseEntity.ok(product);
    }
}
