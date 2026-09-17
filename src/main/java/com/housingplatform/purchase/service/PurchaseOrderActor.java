package com.housingplatform.purchase.service;

import java.util.UUID;

/**
 * Who is calling. Built by the controller from the security context so the service stays free of
 * static security lookups and easy to unit test.
 *
 * @param userId the authenticated user
 * @param organizationId the user's organisation (bank or real estate company), may be null
 * @param agentId the user's real estate agent record, may be null
 * @param admin whether the caller holds an admin scope
 */
public record PurchaseOrderActor(UUID userId, UUID organizationId, UUID agentId, boolean admin) {
  public static PurchaseOrderActor buyer(UUID userId) {
    return new PurchaseOrderActor(userId, null, null, false);
  }
}
