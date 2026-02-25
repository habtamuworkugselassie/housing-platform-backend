package com.housingplatform.construction.repository;

import com.housingplatform.construction.domain.MaterialInventory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MaterialInventoryRepository extends JpaRepository<MaterialInventory, UUID> {

  Optional<MaterialInventory> findByMaterialIdAndProjectId(UUID materialId, UUID projectId);

  Optional<MaterialInventory> findByMaterialIdAndWarehouseLocation(
      UUID materialId, String warehouseLocation);

  List<MaterialInventory> findByMaterialId(UUID materialId);

  List<MaterialInventory> findByProjectId(UUID projectId);

  Page<MaterialInventory> findByStatus(MaterialInventory.InventoryStatus status, Pageable pageable);

  @Query(
      "SELECT i FROM MaterialInventory i WHERE i.availableQuantity <= i.minimumStockLevel AND i.status != 'OUT_OF_STOCK'")
  List<MaterialInventory> findLowStockItems();

  @Query("SELECT i FROM MaterialInventory i WHERE i.projectId = :projectId AND i.status = :status")
  List<MaterialInventory> findByProjectAndStatus(
      @Param("projectId") UUID projectId,
      @Param("status") MaterialInventory.InventoryStatus status);
}
