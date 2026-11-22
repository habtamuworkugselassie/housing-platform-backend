package com.housingplatform.media.event;

import java.util.UUID;

/** An organization's own account uploaded media that now waits for admin review. */
public record OrganizationMediaUploadedEvent(
    UUID organizationId, String organizationName, String uploadedBy, int count) {}
