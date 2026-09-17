package com.housingplatform.purchase.api;

import com.housingplatform.purchase.dto.AgreementTemplateRequest;
import com.housingplatform.purchase.dto.AgreementTemplateResponse;
import com.housingplatform.purchase.dto.PurchaseAgreementResponse;
import com.housingplatform.purchase.service.PurchaseAgreementService;
import com.housingplatform.shared.security.UserContext;
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

/** Admin management of agreement templates and provider-side signatures. */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin - Agreements")
@AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
@RequiredArgsConstructor
public class AdminAgreementController {

  private final PurchaseAgreementService agreementService;

  @GetMapping("/agreement-templates")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @Operation(summary = "List all agreement template versions")
  public ResponseEntity<List<AgreementTemplateResponse>> listTemplates() {
    return ResponseEntity.ok(agreementService.listTemplates());
  }

  @PostMapping("/agreement-templates")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @Operation(
      summary = "Create a new template version",
      description = "Versions are immutable; pass activate=true to make it the current text")
  public ResponseEntity<AgreementTemplateResponse> createTemplate(
      @Valid @RequestBody AgreementTemplateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(agreementService.createTemplateVersion(request));
  }

  @PostMapping("/agreement-templates/{templateId}/activate")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @Operation(
      summary = "Activate a template version (deactivates the previous one of the same type)")
  public ResponseEntity<AgreementTemplateResponse> activate(@PathVariable UUID templateId) {
    return ResponseEntity.ok(agreementService.setTemplateActive(templateId, true));
  }

  @PostMapping("/agreement-templates/{templateId}/deactivate")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @Operation(summary = "Deactivate a template version")
  public ResponseEntity<AgreementTemplateResponse> deactivate(@PathVariable UUID templateId) {
    return ResponseEntity.ok(agreementService.setTemplateActive(templateId, false));
  }

  @PostMapping("/purchase-agreements/{agreementId}/countersign")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @Operation(
      summary = "Countersign on behalf of the provider",
      description = "Only needed when purchase.provider.auto-countersign is false")
  public ResponseEntity<PurchaseAgreementResponse> countersign(@PathVariable UUID agreementId) {
    return ResponseEntity.ok(
        agreementService.countersign(UserContext.getCurrentUserId(), agreementId));
  }

  @PostMapping("/purchase-orders/{orderId}/agreements")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @Operation(summary = "Issue an active template on an order manually")
  public ResponseEntity<PurchaseAgreementResponse> issue(
      @PathVariable UUID orderId, @RequestParam UUID templateId) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(agreementService.issueManually(UserContext.getCurrentUserId(), orderId, templateId));
  }
}
