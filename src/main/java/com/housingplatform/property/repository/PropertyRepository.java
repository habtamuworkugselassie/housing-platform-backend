package com.housingplatform.property.repository;

import com.housingplatform.property.domain.Property;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface PropertyRepository
    extends JpaRepository<Property, UUID>, JpaSpecificationExecutor<Property> {
  List<Property> findByRealEstateCompanyId(UUID companyId);

  List<Property> findByAgentId(UUID agentId);

  List<Property> findByStatus(Property.PropertyStatus status);

  List<Property> findByVerificationStatus(Property.VerificationStatus status);

  List<Property> findByConstructionStatus(Property.ConstructionStatus status);
}
