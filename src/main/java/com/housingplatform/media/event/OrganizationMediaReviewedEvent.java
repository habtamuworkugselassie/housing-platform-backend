package com.housingplatform.media.event;

import java.util.UUID;

/** An admin approved or rejected an organization media item. */
public record OrganizationMediaReviewedEvent(
    UUID organizationId,
    String organizationName,
    String mediaKind,
    String fileName,
    String status,
    String note) {}
