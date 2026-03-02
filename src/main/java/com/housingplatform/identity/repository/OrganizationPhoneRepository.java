package com.housingplatform.identity.repository;

import com.housingplatform.identity.domain.OrganizationPhone;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationPhoneRepository extends JpaRepository<OrganizationPhone, UUID> {

  List<OrganizationPhone> findByOrganizationIdOrderByDisplayOrderAsc(UUID organizationId);

  void deleteByOrganizationId(UUID organizationId);
}
