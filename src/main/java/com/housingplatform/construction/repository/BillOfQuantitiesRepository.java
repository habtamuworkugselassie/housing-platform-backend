package com.housingplatform.construction.repository;

import com.housingplatform.construction.domain.BillOfQuantities;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillOfQuantitiesRepository extends JpaRepository<BillOfQuantities, UUID> {
  List<BillOfQuantities> findByPropertyId(UUID propertyId);

  List<BillOfQuantities> findByProjectId(UUID projectId);

  List<BillOfQuantities> findByProjectIdIn(Collection<UUID> projectIds);

  List<BillOfQuantities> findByPropertyIdIn(Collection<UUID> propertyIds);

  List<BillOfQuantities> findByStatus(BillOfQuantities.BoQStatus status);
}
