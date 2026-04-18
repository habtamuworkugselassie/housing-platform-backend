package com.housingplatform.publicsupport.rag;

import java.util.UUID;

public final class SupportRagIndexEvents {

  private SupportRagIndexEvents() {}

  public record RagIndexOrganizationEvent(UUID organizationId) {}

  public record RagIndexPropertyEvent(UUID propertyId) {}

  public record RagIndexSponsorshipEvent(UUID sponsorshipId) {}

  public record RagDeleteRagSourceEvent(SupportRagSourceType sourceType, UUID sourceId) {}
}
