package com.housingplatform.purchase.dto;

import com.housingplatform.purchase.domain.PropertyPurchaseOrder.PurchaseOrderStatus;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder.PurchaseType;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Optional filters for the admin purchase-order listing. Every field may be null; {@code query}
 * matches the order number, contact phone or contact email (case-insensitive, substring).
 */
public record AdminPurchaseOrderFilter(
    PurchaseOrderStatus status,
    PurchaseType purchaseType,
    UUID realEstateCompanyId,
    UUID bankId,
    UUID buyerId,
    String query,
    LocalDateTime createdFrom,
    LocalDateTime createdTo) {

  public static AdminPurchaseOrderFilter none() {
    return new AdminPurchaseOrderFilter(null, null, null, null, null, null, null, null);
  }
}
