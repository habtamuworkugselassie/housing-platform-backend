package com.housingplatform.purchase.api;

import com.housingplatform.purchase.dto.AgreementSignatureRequest;
import com.housingplatform.purchase.dto.PurchaseAgreementResponse;
import com.housingplatform.purchase.service.PurchaseAgreementService;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/purchase-orders/{orderId}/agreements")
@Tag(name = "Purchase Orders")
@RequiredArgsConstructor
public class PurchaseAgreementController {

  private final PurchaseAgreementService agreementService;
  private final PurchaseOrderActorResolver actors;

  @GetMapping
  @AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
  @Operation(summary = "List the agreements on a purchase order", description = "Without content")
  public ResponseEntity<List<PurchaseAgreementResponse>> list(@PathVariable UUID orderId) {
    return ResponseEntity.ok(agreementService.list(actors.current(), orderId));
  }

  @GetMapping("/{agreementId}")
  @AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
  @Operation(summary = "Get one agreement with its signed text and fingerprint")
  public ResponseEntity<PurchaseAgreementResponse> get(
      @PathVariable UUID orderId, @PathVariable UUID agreementId) {
    return ResponseEntity.ok(agreementService.get(actors.current(), orderId, agreementId));
  }

  @PostMapping("/{agreementId}/sign")
  @AuthPolicyScope(AuthPolicyScope.Policy.BUYER_SECURED)
  @Operation(
      summary = "Buyer signs a follow-up agreement",
      description =
          "The Promise to Purchase is signed inside order creation; this signs the ones issued later")
  public ResponseEntity<PurchaseAgreementResponse> sign(
      @PathVariable UUID orderId,
      @PathVariable UUID agreementId,
      @Valid @RequestBody AgreementSignatureRequest request,
      HttpServletRequest http) {
    return ResponseEntity.ok(
        agreementService.sign(
            actors.current(),
            orderId,
            agreementId,
            request,
            SignatureEvidenceExtractor.from(http)));
  }
}
