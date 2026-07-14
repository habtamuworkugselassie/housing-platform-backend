package com.housingplatform.property.repository;

import com.housingplatform.property.domain.PropertyMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyMessageRepository extends JpaRepository<PropertyMessage, UUID> {
  List<PropertyMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}
