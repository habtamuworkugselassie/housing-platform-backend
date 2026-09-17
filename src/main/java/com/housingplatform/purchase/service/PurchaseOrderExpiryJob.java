package com.housingplatform.purchase.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Expires purchase orders the seller never answered. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PurchaseOrderExpiryJob {

  private final PurchaseOrderService purchaseOrderService;

  @Scheduled(cron = "${purchase.orders.expiry-cron:0 */15 * * * *}")
  public void expireStaleOrders() {
    int expired = purchaseOrderService.expireStaleOrders();
    if (expired > 0) {
      log.info("Expired {} purchase order(s) awaiting seller review", expired);
    }
  }
}
