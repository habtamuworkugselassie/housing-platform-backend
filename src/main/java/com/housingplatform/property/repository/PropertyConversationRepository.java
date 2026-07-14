package com.housingplatform.property.repository;

import com.housingplatform.property.domain.PropertyConversation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PropertyConversationRepository extends JpaRepository<PropertyConversation, UUID> {

  Optional<PropertyConversation> findByBuyerIdAndPropertyId(UUID buyerId, UUID propertyId);

  @Query(
      "SELECT c FROM PropertyConversation c WHERE c.buyer.id = :userId "
          + "OR c.agent.user.id = :userId ORDER BY c.updatedAt DESC")
  List<PropertyConversation> findVisibleToUser(@Param("userId") UUID userId);
}
