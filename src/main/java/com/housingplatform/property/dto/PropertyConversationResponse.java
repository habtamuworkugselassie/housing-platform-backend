package com.housingplatform.property.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PropertyConversationResponse {
  UUID id;
  UUID propertyId;
  String propertyTitle;
  UUID buyerUserId;
  String buyerName;
  UUID agentId;
  UUID agentUserId;
  String agentName;
  LocalDateTime createdAt;
  LocalDateTime updatedAt;
}
