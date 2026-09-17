package com.housingplatform.purchase.service;

import com.housingplatform.purchase.domain.PropertyPurchaseOrder;

/**
 * Out-of-band channels to the buyer: SMS to the mandatory contact phone and email when one was
 * given. The platform has no SMS gateway yet, so the default implementation only logs; wiring a
 * provider means replacing that bean, not touching the order workflow.
 */
public interface PurchaseOrderContactNotifier {
  void notifyBuyer(PropertyPurchaseOrder order, String subject, String message);
}
