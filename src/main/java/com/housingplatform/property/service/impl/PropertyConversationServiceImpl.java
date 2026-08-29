package com.housingplatform.property.service.impl;

import com.housingplatform.identity.domain.RealEstateAgent;
import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.repository.RealEstateAgentRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.property.domain.Property;
import com.housingplatform.property.domain.PropertyConversation;
import com.housingplatform.property.domain.PropertyMessage;
import com.housingplatform.property.dto.CreatePropertyConversationRequest;
import com.housingplatform.property.dto.CreatePropertyMessageRequest;
import com.housingplatform.property.dto.PropertyConversationResponse;
import com.housingplatform.property.dto.PropertyMessageResponse;
import com.housingplatform.property.repository.PropertyConversationRepository;
import com.housingplatform.property.repository.PropertyMessageRepository;
import com.housingplatform.property.repository.PropertyRepository;
import com.housingplatform.property.service.PropertyConversationService;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PropertyConversationServiceImpl implements PropertyConversationService {

  private final PropertyConversationRepository conversationRepository;
  private final PropertyMessageRepository messageRepository;
  private final PropertyRepository propertyRepository;
  private final RealEstateAgentRepository agentRepository;
  private final UserRepository userRepository;

  @Override
  @Transactional
  public PropertyConversationResponse create(
      UUID buyerUserId, CreatePropertyConversationRequest request) {
    User buyer =
        userRepository
            .findById(buyerUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User", buyerUserId));
    Property property =
        propertyRepository
            .findById(request.getPropertyId())
            .orElseThrow(() -> new ResourceNotFoundException("Property", request.getPropertyId()));
    if (property.getAgentId() == null) {
      throw new BusinessException("This property does not have an assigned agent yet");
    }
    RealEstateAgent agent =
        agentRepository
            .findById(property.getAgentId())
            .orElseThrow(
                () -> new BusinessException("The assigned property agent is no longer available"));
    if (agent.getStatus() != RealEstateAgent.AgentStatus.ACTIVE) {
      throw new BusinessException("The assigned property agent is not available for chat");
    }

    PropertyConversation conversation =
        conversationRepository
            .findByBuyerIdAndPropertyId(buyerUserId, property.getId())
            .orElseGet(
                () ->
                    conversationRepository.save(
                        PropertyConversation.builder()
                            .buyer(buyer)
                            .agent(agent)
                            .property(property)
                            .build()));
    createMessage(conversation, buyer, request.getMessage());
    return toConversationResponse(conversation);
  }

  @Override
  @Transactional(readOnly = true)
  public List<PropertyConversationResponse> getConversations(UUID actorUserId) {
    return conversationRepository.findVisibleToUser(actorUserId).stream()
        .map(this::toConversationResponse)
        .toList();
  }

  @Override
  @Transactional
  public List<PropertyMessageResponse> getMessages(UUID actorUserId, UUID conversationId) {
    PropertyConversation conversation = getVisibleConversation(actorUserId, conversationId);
    List<PropertyMessage> messages =
        messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
    LocalDateTime readAt = LocalDateTime.now();
    messages.stream()
        .filter(
            message ->
                !message.getSender().getId().equals(actorUserId) && message.getReadAt() == null)
        .forEach(message -> message.setReadAt(readAt));
    return messages.stream().map(this::toMessageResponse).toList();
  }

  @Override
  @Transactional
  public PropertyMessageResponse sendMessage(
      UUID actorUserId, UUID conversationId, CreatePropertyMessageRequest request) {
    PropertyConversation conversation = getVisibleConversation(actorUserId, conversationId);
    User sender =
        userRepository
            .findById(actorUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User", actorUserId));
    return toMessageResponse(createMessage(conversation, sender, request.getMessage()));
  }

  private PropertyConversation getVisibleConversation(UUID actorUserId, UUID conversationId) {
    PropertyConversation conversation =
        conversationRepository
            .findById(conversationId)
            .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));
    boolean isBuyer = conversation.getBuyer().getId().equals(actorUserId);
    boolean isAgent = conversation.getAgent().getUser().getId().equals(actorUserId);
    if (!isBuyer && !isAgent) {
      throw new BusinessException("You are not a participant in this conversation");
    }
    return conversation;
  }

  private PropertyMessage createMessage(
      PropertyConversation conversation, User sender, String content) {
    PropertyMessage message =
        PropertyMessage.builder()
            .conversation(conversation)
            .sender(sender)
            .content(content.trim())
            .build();
    conversation.setUpdatedAt(LocalDateTime.now());
    return messageRepository.save(message);
  }

  private PropertyConversationResponse toConversationResponse(PropertyConversation conversation) {
    User buyer = conversation.getBuyer();
    User agentUser = conversation.getAgent().getUser();
    return PropertyConversationResponse.builder()
        .id(conversation.getId())
        .propertyId(conversation.getProperty().getId())
        .propertyTitle(conversation.getProperty().getTitle())
        .buyerUserId(buyer.getId())
        .buyerName(fullName(buyer))
        .agentId(conversation.getAgent().getId())
        .agentUserId(agentUser.getId())
        .agentName(fullName(agentUser))
        .createdAt(conversation.getCreatedAt())
        .updatedAt(conversation.getUpdatedAt())
        .build();
  }

  private PropertyMessageResponse toMessageResponse(PropertyMessage message) {
    return PropertyMessageResponse.builder()
        .id(message.getId())
        .conversationId(message.getConversation().getId())
        .senderUserId(message.getSender().getId())
        .senderName(fullName(message.getSender()))
        .message(message.getContent())
        .readAt(message.getReadAt())
        .createdAt(message.getCreatedAt())
        .build();
  }

  private String fullName(User user) {
    return (user.getFirstName() + " " + user.getLastName()).trim();
  }
}
