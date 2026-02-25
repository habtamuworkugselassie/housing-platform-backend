package com.housingplatform.banking.api;

import com.housingplatform.banking.dto.CreditProductRequest;
import com.housingplatform.banking.dto.CreditProductResponse;
import com.housingplatform.banking.dto.FinancingOfferResponse;
import com.housingplatform.banking.service.CreditProductService;
import com.housingplatform.banking.service.FinancingOfferService;
import com.housingplatform.shared.security.annotation.AuthActionScope;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/banks/{bankId}/credit-products")
@Tag(name = "Credit Products", description = "Bank credit product management APIs")
@RequiredArgsConstructor
public class CreditProductController {

  private final CreditProductService creditProductService;
  private final FinancingOfferService financingOfferService;

  @GetMapping
  @AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
  @Operation(
      summary = "List credit products",
      description = "Retrieve all credit products for a bank. Public access.")
  public ResponseEntity<List<CreditProductResponse>> getCreditProducts(
      @PathVariable UUID bankId, @RequestParam(required = false) String status) {
    List<CreditProductResponse> products = creditProductService.getCreditProductsByBankId(bankId);

    // Filter by status if provided
    if (status != null && !status.isEmpty()) {
      products =
          products.stream()
              .filter(p -> p.getStatus().name().equalsIgnoreCase(status))
              .collect(java.util.stream.Collectors.toList());
    }

    return ResponseEntity.ok(products);
  }

  @PostMapping
  @AuthPolicyScope(AuthPolicyScope.Policy.BANKER_SECURED)
  @AuthActionScope("credit-products.create")
  @Operation(
      summary = "Create credit product",
      description = "Create a new credit product for a bank")
  public ResponseEntity<CreditProductResponse> createCreditProduct(
      @PathVariable UUID bankId, @Valid @RequestBody CreditProductRequest productRequest) {
    CreditProductResponse created =
        creditProductService.createCreditProduct(bankId, productRequest);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping("/{productId}")
  @AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
  @Operation(
      summary = "Get credit product",
      description = "Retrieve a specific credit product. Public access.")
  public ResponseEntity<CreditProductResponse> getCreditProduct(
      @PathVariable UUID bankId, @PathVariable UUID productId) {
    CreditProductResponse product = creditProductService.getCreditProductById(productId);
    return ResponseEntity.ok(product);
  }

  @PutMapping("/{productId}")
  @AuthPolicyScope(AuthPolicyScope.Policy.BANKER_SECURED)
  @AuthActionScope("credit-products.update")
  @Operation(summary = "Update credit product", description = "Update an existing credit product")
  public ResponseEntity<CreditProductResponse> updateCreditProduct(
      @PathVariable UUID bankId,
      @PathVariable UUID productId,
      @Valid @RequestBody CreditProductRequest productRequest) {
    CreditProductResponse updated =
        creditProductService.updateCreditProduct(bankId, productId, productRequest);
    return ResponseEntity.ok(updated);
  }

  @DeleteMapping("/{productId}")
  @AuthPolicyScope(AuthPolicyScope.Policy.BANKER_SECURED)
  @AuthActionScope("credit-products.delete")
  @Operation(summary = "Delete credit product", description = "Delete a credit product")
  public ResponseEntity<Void> deleteCreditProduct(
      @PathVariable UUID bankId, @PathVariable UUID productId) {
    creditProductService.deleteCreditProduct(bankId, productId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{productId}/financing-offers")
  @AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
  @Operation(
      summary = "Get financing offers",
      description = "Retrieve all financing offers for a credit product. Public access.")
  public ResponseEntity<List<FinancingOfferResponse>> getFinancingOffers(
      @PathVariable UUID bankId, @PathVariable UUID productId) {
    List<FinancingOfferResponse> offers =
        financingOfferService.getFinancingOffersByProductId(productId);
    return ResponseEntity.ok(offers);
  }
}
