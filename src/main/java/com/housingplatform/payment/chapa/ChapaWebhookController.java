package com.housingplatform.payment.chapa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.housingplatform.purchase.service.PurchaseDepositService;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Receives Chapa's payment notifications. Always answers quickly; the work is a verify call. */
@RestController
@RequestMapping("/api/v1/payments/chapa")
@RequiredArgsConstructor
@Slf4j
@Hidden
public class ChapaWebhookController {

  private final ChapaProperties properties;
  private final PurchaseDepositService depositService;
  private final ObjectMapper objectMapper;

  @PostMapping("/webhook")
  @AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
  public ResponseEntity<Void> webhook(
      @RequestBody String payload,
      @RequestHeader(value = "Chapa-Signature", required = false) String chapaSignature,
      @RequestHeader(value = "x-chapa-signature", required = false) String xChapaSignature) {
    if (!ChapaSignature.matches(
        properties.getWebhookSecret(), payload, chapaSignature, xChapaSignature)) {
      log.warn("Rejected Chapa webhook with a bad or missing signature");
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    String txRef;
    try {
      JsonNode node = objectMapper.readTree(payload);
      txRef = node.path("tx_ref").asText(null);
      if (txRef == null) {
        txRef = node.path("data").path("tx_ref").asText(null);
      }
    } catch (Exception e) {
      return ResponseEntity.badRequest().build();
    }
    if (txRef == null || txRef.isBlank()) {
      return ResponseEntity.badRequest().build();
    }
    try {
      depositService.handleProviderNotification(txRef);
    } catch (RuntimeException e) {
      // Chapa retries on non-2xx; a transient verify failure should be retried, unknown refs not.
      log.warn("Chapa webhook for {} could not be processed: {}", txRef, e.getMessage());
    }
    return ResponseEntity.ok().build();
  }
}
