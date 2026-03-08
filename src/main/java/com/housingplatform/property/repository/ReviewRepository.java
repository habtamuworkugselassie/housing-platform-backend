package com.housingplatform.property.repository;

import com.housingplatform.property.domain.Review;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {
  List<Review> findByPropertyId(UUID propertyId);
}
