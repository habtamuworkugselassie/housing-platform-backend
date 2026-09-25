package com.housingplatform.purchase.api;

import com.housingplatform.payment.chapa.ChapaPaymentMethod;
import com.housingplatform.property.api.DocumentFileResponses;
import com.housingplatform.purchase.dto.DepositCheckoutResponse;
import com.housingplatform.purchase.dto.PurchaseBalanceResponse;
import com.housingplatform.purchase.service.PurchaseBalanceService;
import com.housingplatform.purchase.service.PurchaseBalanceService.InstalmentLine;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Paying the balance of a purchase order to the provider (after the reservation deposit). */
@RestController
@RequestMapping("/api/v1/purchase-orders/{orderId}/balance")
@Tag(name = "Purchase Orders")
@RequiredArgsConstructor
public class PurchaseBalanceController {

  private final PurchaseBalanceService balance;
  private final PurchaseOrderActorResolver actors;

  public record OnlineRequest(
      BigDecimal amount,
      ChapaPaymentMethod paymentMethod,
      com.housingplatform.purchase.domain.PurchaseBalancePayment.Purpose purpose) {}

  public record ScheduleRequest(List<InstalmentLine> instalments) {}

  public record ReviewRequest(boolean approve, String note) {}

  public record RecordRequest(
      BigDecimal amount,
      String reference,
      LocalDate paidOn,
      String note,
      com.housingplatform.purchase.domain.PurchaseBalancePayment.Purpose purpose) {}

  @GetMapping
  @AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
  @Operation(summary = "Balance due, schedule and payments (buyer, seller, bank or admin)")
  public ResponseEntity<PurchaseBalanceResponse> get(@PathVariable UUID orderId) {
    return ResponseEntity.ok(balance.get(actors.current(), orderId));
  }

  // ---- buyer

  @PostMapping("/checkout")
  @AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
  @Operation(
      summary = "Pay part or all of the balance online",
      description =
          "Opens a Chapa checkout for the amount; pay in several parts to stay within wallet"
              + " or card limits.")
  public ResponseEntity<DepositCheckoutResponse> checkout(
      @PathVariable UUID orderId, @RequestBody OnlineRequest request) {
    return ResponseEntity.ok(
        balance.startOnline(
            actors.current(),
            orderId,
            request.amount(),
            request.paymentMethod(),
            request.purpose()));
  }

  @PostMapping("/confirm")
  @AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
  @Operation(summary = "Confirm online payments after returning from Chapa")
  public ResponseEntity<PurchaseBalanceResponse> confirm(@PathVariable UUID orderId) {
    return ResponseEntity.ok(balance.confirmOnline(actors.current(), orderId));
  }

  @PostMapping(value = "/transfers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
  @Operation(
      summary = "Report a bank transfer with its receipt; the seller or an admin confirms it")
  public ResponseEntity<PurchaseBalanceResponse> reportTransfer(
      @PathVariable UUID orderId,
      @RequestParam BigDecimal amount,
      @RequestParam String reference,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate paidOn,
      @RequestParam("slip") MultipartFile slip,
      @RequestParam(required = false)
          com.housingplatform.purchase.domain.PurchaseBalancePayment.Purpose purpose) {
    return ResponseEntity.ok(
        balance.reportTransfer(
            actors.current(), orderId, amount, reference, paidOn, slip, purpose));
  }

  @GetMapping("/payments/{paymentId}/slip")
  @AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
  @Operation(summary = "Open a transfer receipt (buyer, seller or admin)")
  public ResponseEntity<Resource> slip(@PathVariable UUID orderId, @PathVariable UUID paymentId)
      throws IOException {
    return DocumentFileResponses.inline(balance.slip(actors.current(), orderId, paymentId));
  }

  // ---- seller (or admin)

  @PutMapping("/schedule")
  @AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
  @Operation(summary = "Set the instalment schedule; lines must add up to the balance due")
  public ResponseEntity<PurchaseBalanceResponse> schedule(
      @PathVariable UUID orderId, @RequestBody ScheduleRequest request) {
    return ResponseEntity.ok(balance.setSchedule(actors.current(), orderId, request.instalments()));
  }

  @PostMapping("/payments/{paymentId}/review")
  @AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
  @Operation(summary = "Confirm or reject a reported bank transfer")
  public ResponseEntity<PurchaseBalanceResponse> review(
      @PathVariable UUID orderId,
      @PathVariable UUID paymentId,
      @RequestBody ReviewRequest request) {
    return ResponseEntity.ok(
        balance.reviewTransfer(
            actors.current(), orderId, paymentId, request.approve(), request.note()));
  }

  @PostMapping("/payments")
  @AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
  @Operation(summary = "Record money received outside the platform")
  public ResponseEntity<PurchaseBalanceResponse> record(
      @PathVariable UUID orderId, @RequestBody RecordRequest request) {
    return ResponseEntity.ok(
        balance.record(
            actors.current(),
            orderId,
            request.amount(),
            request.reference(),
            request.paidOn(),
            request.note(),
            request.purpose()));
  }
}
