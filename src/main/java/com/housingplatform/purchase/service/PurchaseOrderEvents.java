package com.housingplatform.purchase.service;

import com.housingplatform.purchase.domain.PropertyPurchaseOrder.PurchaseOrderStatus;
import java.util.UUID;

/** In-process events raised by the purchase module. Listeners run after commit. */
public final class PurchaseOrderEvents {
  private PurchaseOrderEvents() {}

  public record PurchaseOrderCreatedEvent(UUID purchaseOrderId) {}

  public record PurchaseOrderStatusChangedEvent(
      UUID purchaseOrderId, PurchaseOrderStatus fromStatus, PurchaseOrderStatus toStatus) {}

  /** A follow-up agreement was issued and awaits the buyer's signature. */
  public record PurchaseAgreementIssuedEvent(UUID purchaseOrderId, UUID agreementId) {}
}
