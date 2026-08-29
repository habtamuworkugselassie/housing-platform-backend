package com.housingplatform.property.api;

import com.housingplatform.property.dto.CreatePropertyConversationRequest;
import com.housingplatform.property.dto.CreatePropertyMessageRequest;
import com.housingplatform.property.dto.PropertyConversationResponse;
import com.housingplatform.property.dto.PropertyMessageResponse;
import com.housingplatform.property.service.PropertyConversationService;
import com.housingplatform.shared.security.UserContext;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/property-conversations")
@AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
@Tag(
    name = "Property conversations",
    description = "Buyer and assigned-agent listing conversations")
@RequiredArgsConstructor
public class PropertyConversationController {

  private final PropertyConversationService propertyConversationService;

  @PostMapping
  @AuthPolicyScope(AuthPolicyScope.Policy.BUYER_SECURED)
  @Operation(summary = "Start a conversation about a property")
  public ResponseEntity<PropertyConversationResponse> create(
      @Valid @RequestBody CreatePropertyConversationRequest request) {
    PropertyConversationResponse response =
        propertyConversationService.create(UserContext.getCurrentUserId(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping
  @Operation(summary = "List the current user's property conversations")
  public ResponseEntity<List<PropertyConversationResponse>> getConversations() {
    return ResponseEntity.ok(
        propertyConversationService.getConversations(UserContext.getCurrentUserId()));
  }

  @GetMapping("/{conversationId}/messages")
  @Operation(summary = "List messages and mark received messages as read")
  public ResponseEntity<List<PropertyMessageResponse>> getMessages(
      @PathVariable UUID conversationId) {
    return ResponseEntity.ok(
        propertyConversationService.getMessages(UserContext.getCurrentUserId(), conversationId));
  }

  @PostMapping("/{conversationId}/messages")
  @Operation(summary = "Send a message in a property conversation")
  public ResponseEntity<PropertyMessageResponse> sendMessage(
      @PathVariable UUID conversationId, @Valid @RequestBody CreatePropertyMessageRequest request) {
    PropertyMessageResponse response =
        propertyConversationService.sendMessage(
            UserContext.getCurrentUserId(), conversationId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
