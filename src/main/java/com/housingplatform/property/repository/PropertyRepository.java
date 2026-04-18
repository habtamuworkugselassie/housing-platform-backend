package com.housingplatform.property.repository;

import com.housingplatform.property.domain.Property;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface PropertyRepository
    extends JpaRepository<Property, UUID>, JpaSpecificationExecutor<Property> {
  @org.springframework.data.jpa.repository.Query("SELECT p.id FROM Property p")
  List<UUID> findAllIds();

  List<Property> findByRealEstateCompanyId(UUID companyId);

  List<Property> findByAgentId(UUID agentId);

  List<Property> findByStatus(Property.PropertyStatus status);

  /** Recent listings first (for support-chat directory ranking over a bounded pool). */
  List<Property> findByStatusOrderByCreatedAtDesc(
      Property.PropertyStatus status, Pageable pageable);

  List<Property> findByVerificationStatus(Property.VerificationStatus status);

  List<Property> findByConstructionStatus(Property.ConstructionStatus status);
}
