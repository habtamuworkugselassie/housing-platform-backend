package com.housingplatform.construction.service;

import com.housingplatform.construction.domain.MaterialInventory;
import com.housingplatform.construction.dto.MaterialInventoryRequest;
import com.housingplatform.construction.dto.MaterialInventoryResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MaterialInventoryService {
  MaterialInventoryResponse createInventory(MaterialInventoryRequest request);

  MaterialInventoryResponse getInventoryById(UUID id);

  MaterialInventoryResponse getInventoryByMaterialAndProject(UUID materialId, UUID projectId);

  List<MaterialInventoryResponse> getInventoryByMaterial(UUID materialId);

  List<MaterialInventoryResponse> getInventoryByProject(UUID projectId);

  Page<MaterialInventoryResponse> getInventoryByStatus(
      MaterialInventory.InventoryStatus status, Pageable pageable);

  MaterialInventoryResponse updateInventory(UUID id, MaterialInventoryRequest request);

  MaterialInventoryResponse adjustInventory(
      UUID id, java.math.BigDecimal quantityChange, String reason);

  MaterialInventoryResponse reserveInventory(UUID id, java.math.BigDecimal quantity);

  MaterialInventoryResponse releaseReservation(UUID id, java.math.BigDecimal quantity);

  List<MaterialInventoryResponse> getLowStockItems();

  void deleteInventory(UUID id);
}
