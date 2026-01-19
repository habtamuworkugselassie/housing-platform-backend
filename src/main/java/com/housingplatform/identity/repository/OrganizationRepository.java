package com.housingplatform.identity.repository;

import com.housingplatform.identity.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    Optional<Organization> findByRegistrationNumber(String registrationNumber);
    List<Organization> findByType(Organization.OrganizationType type);
    List<Organization> findByStatus(Organization.OrganizationStatus status);
    List<Organization> findByTypeAndStatus(Organization.OrganizationType type, Organization.OrganizationStatus status);
}
