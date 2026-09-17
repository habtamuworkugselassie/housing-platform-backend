package com.housingplatform.purchase.service;

import com.housingplatform.purchase.domain.PropertyPurchaseOrder;
import com.housingplatform.purchase.dto.DepositCheckoutResponse;
import com.housingplatform.purchase.dto.PurchaseDepositResponse;
import java.util.UUID;

/**
 * Reservation deposit paid by the buyer to the provider through Chapa's hosted checkout.
 *
 * <p>Lifecycle: issued on seller acceptance (DUE) → buyer starts a checkout (PENDING) → the
 * provider's webhook or the buyer's return confirms it (PAID / FAILED). Closing the order cancels
 * an unpaid deposit and flags a paid one for refund.
 */
public interface PurchaseDepositService {

  // ---- order workflow hooks (same transaction as the order)

  /** Creates the DUE deposit for a freshly accepted order; no-op when deposits are disabled. */
  void issueForOrder(PropertyPurchaseOrder order);

  /** Cancels an unpaid deposit or flags a paid one for refund when the order closes. */
  void onOrderClosed(PropertyPurchaseOrder order, String reason);

  /** True when the order requires a deposit that is neither paid nor waived. */
  boolean blocksCompletion(PropertyPurchaseOrder order);

  // ---- buyer

  PurchaseDepositResponse get(PurchaseOrderActor actor, UUID orderId);

  /** Starts (or restarts after a failure) a hosted checkout and returns where to send the buyer. */
  DepositCheckoutResponse startCheckout(PurchaseOrderActor buyer, UUID orderId);

  /**
   * Called when the buyer returns from the provider: confirms the result through the verify API.
   */
  PurchaseDepositResponse confirm(PurchaseOrderActor buyer, UUID orderId);

  // ---- provider

  /** Webhook entry point: verifies the referenced transaction and records the outcome. */
  void handleProviderNotification(String txRef);

  // ---- admin

  PurchaseDepositResponse waive(UUID adminUserId, UUID orderId, String reason);

  PurchaseDepositResponse markRefunded(UUID adminUserId, UUID orderId, String refundReference);

  PurchaseDepositResponse toResponse(PropertyPurchaseOrder order);
}
