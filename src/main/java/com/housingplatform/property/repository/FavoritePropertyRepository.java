package com.housingplatform.property.repository;

import com.housingplatform.property.domain.FavoriteProperty;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoritePropertyRepository extends JpaRepository<FavoriteProperty, UUID> {
  List<FavoriteProperty> findByUserIdOrderByCreatedAtDesc(UUID userId);

  boolean existsByUserIdAndPropertyId(UUID userId, UUID propertyId);

  void deleteByUserIdAndPropertyId(UUID userId, UUID propertyId);
}
