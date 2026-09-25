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
  private final PurchaseOrderActorResolver actors;
  private final com.housingplatform.property.service.PropertyDocumentService documentService;

  @GetMapping
  @AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
  @Operation(
      summary = "Preview a purchase order",
      description =
          "Shows whether an order on this property would be cash or bank financed, and the"
              + " financing range for each eligible offer, plus the agreements the buyer must sign"
              + " to create the order. Public: visitors see it before signing up; a signed-in"
              + " buyer gets the agreement rendered with their name. No side effects.")
  public ResponseEntity<PurchasePreviewResponse> preview(
      @PathVariable UUID propertyId,
      @RequestParam(required = false) Currency currency,
      @RequestParam(required = false) Currency depositCurrency) {
    return ResponseEntity.ok(
        purchaseOrderService.preview(
            actors.currentOrAnonymous(), propertyId, currency, depositCurrency));
  }

  @GetMapping("/documents/{documentId}/file")
  @AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
  @Operation(
      summary = "Open a verified property document before ordering",
      description = "For signed-in buyers reviewing Annex A of the Promise to Purchase. Logged.")
  public ResponseEntity<org.springframework.core.io.Resource> documentFile(
      @PathVariable UUID propertyId, @PathVariable UUID documentId) throws java.io.IOException {
    return com.housingplatform.property.api.DocumentFileResponses.inline(
        documentService.verifiedFile(propertyId, documentId));
  }
}
