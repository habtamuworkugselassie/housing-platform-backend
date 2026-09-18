package com.housingplatform.purchase.dto;

import com.housingplatform.purchase.domain.PropertyPurchaseOrder.PurchaseOrderStatus;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Counts for the admin overview: every status appears, with 0 when there are no orders. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderStatsResponse {
  private long total;

  /** Orders that can still progress (not completed, cancelled, rejected or expired). */
  private long open;

  private Map<PurchaseOrderStatus, Long> byStatus;
}
