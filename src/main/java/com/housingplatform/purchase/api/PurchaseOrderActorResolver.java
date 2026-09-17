package com.housingplatform.purchase.api;

import com.housingplatform.identity.repository.RealEstateAgentRepository;
import com.housingplatform.purchase.service.PurchaseOrderActor;
import com.housingplatform.shared.security.UserContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Builds the {@link PurchaseOrderActor} for the current request from the security context. */
@Component
@RequiredArgsConstructor
public class PurchaseOrderActorResolver {

  private final RealEstateAgentRepository agentRepository;

  public PurchaseOrderActor current() {
    UUID userId = UserContext.getCurrentUserId();
    UUID organizationId = UserContext.getCurrentUserOrganizationId().orElse(null);
    UUID agentId = agentRepository.findByUserId(userId).map(a -> a.getId()).orElse(null);
    return new PurchaseOrderActor(userId, organizationId, agentId, UserContext.isAdmin());
  }
}
