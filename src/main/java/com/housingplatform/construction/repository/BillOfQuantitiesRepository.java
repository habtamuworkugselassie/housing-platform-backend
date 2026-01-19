package com.housingplatform.construction.repository;

import com.housingplatform.construction.domain.BillOfQuantities;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BillOfQuantitiesRepository extends JpaRepository<BillOfQuantities, UUID> {
    List<BillOfQuantities> findByPropertyId(UUID propertyId);
    List<BillOfQuantities> findByProjectId(UUID projectId);
    List<BillOfQuantities> findByStatus(BillOfQuantities.BoQStatus status);
}
