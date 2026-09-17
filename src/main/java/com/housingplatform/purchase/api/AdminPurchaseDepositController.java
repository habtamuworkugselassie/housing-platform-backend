package com.housingplatform.purchase.api;

import com.housingplatform.purchase.dto.DepositAdminRequest;
import com.housingplatform.purchase.dto.PurchaseDepositResponse;
import com.housingplatform.purchase.service.PurchaseDepositService;
import com.housingplatform.shared.security.UserContext;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/purchase-orders/{orderId}/deposit")
@Tag(name = "Admin - Purchase Orders")
@AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
@RequiredArgsConstructor
public class AdminPurchaseDepositController {

  private final PurchaseDepositService depositService;

  @PostMapping("/waive")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @Operation(summary = "Waive the reservation deposit on an order")
  public ResponseEntity<PurchaseDepositResponse> waive(
      @PathVariable UUID orderId,
      @Valid @RequestBody(required = false) DepositAdminRequest request) {
    return ResponseEntity.ok(
        depositService.waive(
            UserContext.getCurrentUserId(), orderId, request != null ? request.getReason() : null));
  }

  @PostMapping("/refunded")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @Operation(
      summary = "Record that a paid deposit was refunded",
      description = "Refunds are executed in the provider's dashboard; this records the reference.")
  public ResponseEntity<PurchaseDepositResponse> refunded(
      @PathVariable UUID orderId, @Valid @RequestBody DepositAdminRequest request) {
    return ResponseEntity.ok(
        depositService.markRefunded(
            UserContext.getCurrentUserId(), orderId, request.getRefundReference()));
  }
}
