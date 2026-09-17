package com.housingplatform.purchase.api;

import com.housingplatform.purchase.dto.PurchasePreviewResponse;
import com.housingplatform.purchase.service.PurchaseOrderService;
import com.housingplatform.shared.domain.Currency;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/purchase-preview")
@Tag(name = "Purchase Orders")
@RequiredArgsConstructor
public class PropertyPurchasePreviewController {

  private final PurchaseOrderService purchaseOrderService;

  @GetMapping
  @AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
  @Operation(
      summary = "Preview a purchase order",
      description =
          "Shows whether an order on this property would be cash or bank financed, and the"
              + " financing range for each eligible offer. No side effects.")
  public ResponseEntity<PurchasePreviewResponse> preview(
      @PathVariable UUID propertyId, @RequestParam(required = false) Currency currency) {
    return ResponseEntity.ok(purchaseOrderService.preview(propertyId, currency));
  }
}
