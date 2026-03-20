package com.housingplatform.identity.repository;

import com.housingplatform.identity.domain.Organization;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
  Optional<Organization> findByRegistrationNumber(String registrationNumber);

  List<Organization> findByType(Organization.OrganizationType type);

  List<Organization> findByStatus(Organization.OrganizationStatus status);

  List<Organization> findByTypeAndStatus(
      Organization.OrganizationType type, Organization.OrganizationStatus status);

  @Query(
      "SELECT DISTINCT o FROM Organization o LEFT JOIN o.contact c WHERE "
          + "LOWER(o.name) LIKE LOWER(CONCAT('%', :search, '%')) OR "
          + "(c IS NOT NULL AND LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%'))) OR "
          + "LOWER(o.registrationNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR "
          + "LOWER(o.city) LIKE LOWER(CONCAT('%', :search, '%'))")
  List<Organization> searchOrganizations(@Param("search") String search);
}
