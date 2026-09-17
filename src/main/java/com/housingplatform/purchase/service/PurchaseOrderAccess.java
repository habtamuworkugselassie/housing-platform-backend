package com.housingplatform.purchase.service;

import com.housingplatform.purchase.domain.PropertyPurchaseOrder;
import java.util.UUID;

/** Ownership rules shared by the order and agreement services. */
public final class PurchaseOrderAccess {
  private PurchaseOrderAccess() {}

  public static boolean isBuyer(PurchaseOrderActor actor, PropertyPurchaseOrder order) {
    return actor.admin() || order.getBuyerId().equals(actor.userId());
  }

  public static boolean canSell(PurchaseOrderActor actor, PropertyPurchaseOrder order) {
    return actor.admin()
        || sameOrganization(actor, order.getRealEstateCompanyId())
        || sameAgent(actor, order.getAgentId());
  }

  public static boolean canView(PurchaseOrderActor actor, PropertyPurchaseOrder order) {
    if (isBuyer(actor, order) || canSell(actor, order)) {
      return true;
    }
    return order.getFinancing() != null
        && actor.organizationId() != null
        && actor.organizationId().equals(order.getFinancing().getBankId());
  }

  public static boolean sameOrganization(PurchaseOrderActor actor, UUID organizationId) {
    return actor.organizationId() != null && actor.organizationId().equals(organizationId);
  }

  public static boolean sameAgent(PurchaseOrderActor actor, UUID agentId) {
    return actor.agentId() != null && actor.agentId().equals(agentId);
  }
}
