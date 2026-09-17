package com.housingplatform.purchase.api;

import com.housingplatform.purchase.dto.DepositCheckoutResponse;
import com.housingplatform.purchase.dto.PurchaseDepositResponse;
import com.housingplatform.purchase.service.PurchaseDepositService;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/purchase-orders/{orderId}/deposit")
@Tag(name = "Purchase Orders")
@RequiredArgsConstructor
public class PurchaseDepositController {

  private final PurchaseDepositService depositService;
  private final PurchaseOrderActorResolver actors;

  @GetMapping
  @AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
  @Operation(summary = "Reservation deposit on an order")
  public ResponseEntity<PurchaseDepositResponse> get(@PathVariable UUID orderId) {
    return ResponseEntity.ok(depositService.get(actors.current(), orderId));
  }

  @PostMapping("/checkout")
  @AuthPolicyScope(AuthPolicyScope.Policy.BUYER_SECURED)
  @Operation(
      summary = "Start paying the reservation deposit",
      description =
          "Opens a hosted checkout at the payment provider (Chapa) and returns its URL. Cards are"
              + " entered there, never on this platform. Requires the deposit terms to be signed.")
  public ResponseEntity<DepositCheckoutResponse> checkout(@PathVariable UUID orderId) {
    return ResponseEntity.ok(depositService.startCheckout(actors.current(), orderId));
  }

  @PostMapping("/confirm")
  @AuthPolicyScope(AuthPolicyScope.Policy.BUYER_SECURED)
  @Operation(
      summary = "Confirm the deposit after returning from the provider",
      description = "Asks the provider for the transaction result; idempotent.")
  public ResponseEntity<PurchaseDepositResponse> confirm(@PathVariable UUID orderId) {
    return ResponseEntity.ok(depositService.confirm(actors.current(), orderId));
  }
}
