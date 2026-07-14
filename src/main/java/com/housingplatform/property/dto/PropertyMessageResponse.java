package com.housingplatform.property.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PropertyMessageResponse {
  UUID id;
  UUID conversationId;
  UUID senderUserId;
  String senderName;
  String message;
  LocalDateTime readAt;
  LocalDateTime createdAt;
}
