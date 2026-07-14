package com.housingplatform.property.service;

import com.housingplatform.property.dto.CreatePropertyConversationRequest;
import com.housingplatform.property.dto.CreatePropertyMessageRequest;
import com.housingplatform.property.dto.PropertyConversationResponse;
import com.housingplatform.property.dto.PropertyMessageResponse;
import java.util.List;
import java.util.UUID;

public interface PropertyConversationService {
  PropertyConversationResponse create(UUID buyerUserId, CreatePropertyConversationRequest request);

  List<PropertyConversationResponse> getConversations(UUID actorUserId);

  List<PropertyMessageResponse> getMessages(UUID actorUserId, UUID conversationId);

  PropertyMessageResponse sendMessage(
      UUID actorUserId, UUID conversationId, CreatePropertyMessageRequest request);
}
