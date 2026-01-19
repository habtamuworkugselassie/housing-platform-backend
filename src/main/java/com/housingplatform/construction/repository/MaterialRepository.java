package com.housingplatform.construction.repository;

import com.housingplatform.construction.domain.Material;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MaterialRepository extends JpaRepository<Material, UUID>, JpaSpecificationExecutor<Material> {
    List<Material> findBySupplierId(UUID supplierId);
    List<Material> findByCategory(String category);
    List<Material> findByStatus(Material.MaterialStatus status);
    List<Material> findBySupplierIdAndStatus(UUID supplierId, Material.MaterialStatus status);
}
