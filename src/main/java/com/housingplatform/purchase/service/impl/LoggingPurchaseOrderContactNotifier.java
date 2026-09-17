package com.housingplatform.purchase.service.impl;

import com.housingplatform.purchase.domain.PropertyPurchaseOrder;
import com.housingplatform.purchase.service.PurchaseOrderContactNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Default channel implementation: logs instead of sending. Replace with an SMS/email gateway bean.
 */
@Component
@Slf4j
public class LoggingPurchaseOrderContactNotifier implements PurchaseOrderContactNotifier {

  @Override
  public void notifyBuyer(PropertyPurchaseOrder order, String subject, String message) {
    log.info(
        "[purchase-order {}] SMS to {}: {} — {}",
        order.getOrderNumber(),
        order.getContactPhone(),
        subject,
        message);
    if (order.getContactEmail() != null) {
      log.info(
          "[purchase-order {}] email to {}: {} — {}",
          order.getOrderNumber(),
          order.getContactEmail(),
          subject,
          message);
    }
  }
}
