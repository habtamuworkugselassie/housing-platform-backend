package com.housingplatform.purchase.service;

import com.housingplatform.payment.chapa.ChapaPaymentMethod;
import com.housingplatform.property.service.PropertyDocumentService.DocumentFile;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder;
import com.housingplatform.purchase.domain.PurchaseBalancePayment.Purpose;
import com.housingplatform.purchase.dto.DepositCheckoutResponse;
import com.housingplatform.purchase.dto.PurchaseBalanceResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/**
 * The balance of a purchase order, collected by the provider: price minus the reservation deposit
 * and any bank loan. The seller sets a schedule; the buyer pays online through Chapa in as many
 * payments as their wallet or card limits need, or by bank transfer with a slip that the seller or
 * an admin confirms. The provider's service fee (markup + VAT) is paid the same way, as payments
 * with purpose FEES. The sale cannot complete until both are covered.
 */
public interface PurchaseBalanceService {

  record InstalmentLine(String label, BigDecimal amount, LocalDate dueDate) {}

  PurchaseBalanceResponse get(PurchaseOrderActor actor, UUID orderId);

  // ---- seller (or admin)

  /** Replaces the schedule; lines must add up to the balance due. Locked once money is paid. */
  PurchaseBalanceResponse setSchedule(
      PurchaseOrderActor seller, UUID orderId, List<InstalmentLine> lines);

  /** Confirms or rejects a reported bank transfer. */
  PurchaseBalanceResponse reviewTransfer(
      PurchaseOrderActor seller, UUID orderId, UUID paymentId, boolean approve, String note);

  /** Records money received outside the platform (cheque, direct transfer). */
  default PurchaseBalanceResponse record(
      PurchaseOrderActor seller,
      UUID orderId,
      BigDecimal amount,
      String reference,
      LocalDate paidOn,
      String note) {
    return record(seller, orderId, amount, reference, paidOn, note, Purpose.BALANCE);
  }

  PurchaseBalanceResponse record(
      PurchaseOrderActor seller,
      UUID orderId,
      BigDecimal amount,
      String reference,
      LocalDate paidOn,
      String note,
      Purpose purpose);

  // ---- buyer

  default DepositCheckoutResponse startOnline(
      PurchaseOrderActor buyer, UUID orderId, BigDecimal amount, ChapaPaymentMethod method) {
    return startOnline(buyer, orderId, amount, method, Purpose.BALANCE);
  }

  /** {@code purpose} FEES pays the service fee (markup + VAT) instead of the balance. */
  DepositCheckoutResponse startOnline(
      PurchaseOrderActor buyer,
      UUID orderId,
      BigDecimal amount,
      ChapaPaymentMethod method,
      Purpose purpose);

  /** After returning from Chapa: settles the order's pending online payments via verify. */
  PurchaseBalanceResponse confirmOnline(PurchaseOrderActor buyer, UUID orderId);

  default PurchaseBalanceResponse reportTransfer(
      PurchaseOrderActor buyer,
      UUID orderId,
      BigDecimal amount,
      String reference,
      LocalDate paidOn,
      MultipartFile slip) {
    return reportTransfer(buyer, orderId, amount, reference, paidOn, slip, Purpose.BALANCE);
  }

  PurchaseBalanceResponse reportTransfer(
      PurchaseOrderActor buyer,
      UUID orderId,
      BigDecimal amount,
      String reference,
      LocalDate paidOn,
      MultipartFile slip,
      Purpose purpose);

  DocumentFile slip(PurchaseOrderActor actor, UUID orderId, UUID paymentId) throws IOException;

  // ---- provider

  boolean ownsTxRef(String txRef);

  void handleProviderNotification(String txRef);

  // ---- order workflow

  /** True while an accepted order's balance is not fully paid. */
  boolean blocksCompletion(PropertyPurchaseOrder order);

  /** Cancels payments in flight and flags paid ones for refund. */
  void onOrderClosed(PropertyPurchaseOrder order);
}
