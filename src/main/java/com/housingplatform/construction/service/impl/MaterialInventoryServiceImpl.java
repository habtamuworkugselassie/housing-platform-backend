package com.housingplatform.construction.service.impl;

import com.housingplatform.construction.domain.MaterialInventory;
import com.housingplatform.construction.dto.MaterialInventoryRequest;
import com.housingplatform.construction.dto.MaterialInventoryResponse;
import com.housingplatform.construction.repository.MaterialInventoryRepository;
import com.housingplatform.construction.repository.MaterialRepository;
import com.housingplatform.construction.service.ConstructionAccessGuard;
import com.housingplatform.construction.service.MaterialInventoryMapper;
import com.housingplatform.construction.service.MaterialInventoryService;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MaterialInventoryServiceImpl implements MaterialInventoryService {

  private final MaterialInventoryRepository inventoryRepository;
  private final MaterialRepository materialRepository;
  private final MaterialInventoryMapper inventoryMapper;
  private final ConstructionAccessGuard accessGuard;

  /**
   * Loads inventory the caller may access, or throws not-found. Project inventory follows project
   * access; warehouse inventory (no projectId) is admin-only — see {@link
   * ConstructionAccessGuard#canAccessInventory}.
   */
  private MaterialInventory requireInventory(UUID id) {
    MaterialInventory inventory =
        inventoryRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Material Inventory", id));
    accessGuard.checkInventory(inventory);
    return inventory;
  }

  /** Non-admins only see inventory belonging to projects they can access. */
  private List<MaterialInventory> scoped(List<MaterialInventory> items) {
    if (accessGuard.isAdmin()) {
      return items;
    }
    Set<UUID> projectIds = accessGuard.accessibleProjectIds();
    return items.stream()
        .filter(i -> i.getProjectId() != null && projectIds.contains(i.getProjectId()))
        .collect(Collectors.toList());
  }

  @Override
  public MaterialInventoryResponse createInventory(MaterialInventoryRequest request) {
    // Project inventory requires project access; warehouse inventory (no project) is admin-only
    // because it has no owner the caller could later be matched against.
    if (request.getProjectId() != null) {
      accessGuard.requireProject(request.getProjectId());
    } else if (!accessGuard.isAdmin()) {
      throw new BusinessException("Inventory must be assigned to a construction project");
    }

    // Validate material exists
    materialRepository
        .findById(request.getMaterialId())
        .orElseThrow(() -> new ResourceNotFoundException("Material", request.getMaterialId()));

    // Check if inventory already exists for this material and project/location
    MaterialInventory existing = null;
    if (request.getProjectId() != null) {
      existing =
          inventoryRepository
              .findByMaterialIdAndProjectId(request.getMaterialId(), request.getProjectId())
              .orElse(null);
    } else {
      existing =
          inventoryRepository
              .findByMaterialIdAndWarehouseLocation(
                  request.getMaterialId(), request.getWarehouseLocation())
              .orElse(null);
    }

    if (existing != null) {
      throw new BusinessException(
          "Inventory already exists for this material and location/project");
    }

    MaterialInventory inventory = inventoryMapper.toEntity(request);
    inventory.setReservedQuantity(BigDecimal.ZERO);
    inventory.setAvailableQuantity(request.getQuantity());

    // Calculate total value
    if (request.getUnitCost() != null) {
      inventory.setTotalValue(request.getQuantity().multiply(request.getUnitCost()));
    }

    // Set status based on quantity
    if (request.getMinimumStockLevel() != null
        && request.getQuantity().compareTo(request.getMinimumStockLevel()) <= 0) {
      inventory.setStatus(MaterialInventory.InventoryStatus.LOW_STOCK);
    } else if (request.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
      inventory.setStatus(MaterialInventory.InventoryStatus.OUT_OF_STOCK);
    } else {
      inventory.setStatus(MaterialInventory.InventoryStatus.IN_STOCK);
    }

    MaterialInventory saved = inventoryRepository.save(inventory);
    return enrichInventoryResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public MaterialInventoryResponse getInventoryById(UUID id) {
    MaterialInventory inventory = requireInventory(id);
    return enrichInventoryResponse(inventory);
  }

  @Override
  @Transactional(readOnly = true)
  public MaterialInventoryResponse getInventoryByMaterialAndProject(
      UUID materialId, UUID projectId) {
    accessGuard.requireProject(projectId);
    MaterialInventory inventory =
        inventoryRepository
            .findByMaterialIdAndProjectId(materialId, projectId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Material Inventory",
                        "materialId: " + materialId + ", projectId: " + projectId));
    return enrichInventoryResponse(inventory);
  }

  @Override
  @Transactional(readOnly = true)
  public List<MaterialInventoryResponse> getInventoryByMaterial(UUID materialId) {
    return scoped(inventoryRepository.findByMaterialId(materialId)).stream()
        .map(this::enrichInventoryResponse)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<MaterialInventoryResponse> getInventoryByProject(UUID projectId) {
    accessGuard.requireProject(projectId);
    return inventoryRepository.findByProjectId(projectId).stream()
        .map(this::enrichInventoryResponse)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public Page<MaterialInventoryResponse> getInventoryByStatus(
      MaterialInventory.InventoryStatus status, Pageable pageable) {
    if (accessGuard.isAdmin()) {
      return inventoryRepository.findByStatus(status, pageable).map(this::enrichInventoryResponse);
    }
    Set<UUID> projectIds = accessGuard.accessibleProjectIds();
    if (projectIds.isEmpty()) {
      return Page.empty(pageable);
    }
    return inventoryRepository
        .findByStatusAndProjectIdIn(status, projectIds, pageable)
        .map(this::enrichInventoryResponse);
  }

  @Override
  public MaterialInventoryResponse updateInventory(UUID id, MaterialInventoryRequest request) {
    MaterialInventory inventory = requireInventory(id);
    // Moving inventory to another project requires access to the target project too.
    if (request.getProjectId() != null
        && !request.getProjectId().equals(inventory.getProjectId())) {
      accessGuard.requireProject(request.getProjectId());
    }

    inventoryMapper.updateEntity(inventory, request);

    // Recalculate available quantity
    inventory.setAvailableQuantity(
        inventory.getQuantity().subtract(inventory.getReservedQuantity()));

    // Recalculate total value
    if (inventory.getUnitCost() != null) {
      inventory.setTotalValue(inventory.getQuantity().multiply(inventory.getUnitCost()));
    }

    // Update status
    updateInventoryStatus(inventory);

    MaterialInventory updated = inventoryRepository.save(inventory);
    return enrichInventoryResponse(updated);
  }

  @Override
  public MaterialInventoryResponse adjustInventory(
      UUID id, BigDecimal quantityChange, String reason) {
    MaterialInventory inventory = requireInventory(id);

    BigDecimal newQuantity = inventory.getQuantity().add(quantityChange);
    if (newQuantity.compareTo(BigDecimal.ZERO) < 0) {
      throw new BusinessException("Cannot adjust inventory below zero");
    }

    inventory.setQuantity(newQuantity);
    inventory.setAvailableQuantity(newQuantity.subtract(inventory.getReservedQuantity()));

    if (inventory.getUnitCost() != null) {
      inventory.setTotalValue(newQuantity.multiply(inventory.getUnitCost()));
    }

    if (reason != null && !reason.isEmpty()) {
      String currentNotes = inventory.getNotes() != null ? inventory.getNotes() : "";
      inventory.setNotes(currentNotes + "\n" + reason);
    }

    updateInventoryStatus(inventory);

    MaterialInventory updated = inventoryRepository.save(inventory);
    return enrichInventoryResponse(updated);
  }

  @Override
  public MaterialInventoryResponse reserveInventory(UUID id, BigDecimal quantity) {
    MaterialInventory inventory = requireInventory(id);

    BigDecimal newReserved = inventory.getReservedQuantity().add(quantity);
    BigDecimal available = inventory.getQuantity().subtract(newReserved);

    if (available.compareTo(BigDecimal.ZERO) < 0) {
      throw new BusinessException("Cannot reserve more than available quantity");
    }

    inventory.setReservedQuantity(newReserved);
    inventory.setAvailableQuantity(available);
    inventory.setStatus(MaterialInventory.InventoryStatus.RESERVED);

    MaterialInventory updated = inventoryRepository.save(inventory);
    return enrichInventoryResponse(updated);
  }

  @Override
  public MaterialInventoryResponse releaseReservation(UUID id, BigDecimal quantity) {
    MaterialInventory inventory = requireInventory(id);

    BigDecimal newReserved = inventory.getReservedQuantity().subtract(quantity);
    if (newReserved.compareTo(BigDecimal.ZERO) < 0) {
      throw new BusinessException("Cannot release more than reserved quantity");
    }

    inventory.setReservedQuantity(newReserved);
    inventory.setAvailableQuantity(inventory.getQuantity().subtract(newReserved));

    updateInventoryStatus(inventory);

    MaterialInventory updated = inventoryRepository.save(inventory);
    return enrichInventoryResponse(updated);
  }

  @Override
  @Transactional(readOnly = true)
  public List<MaterialInventoryResponse> getLowStockItems() {
    return scoped(inventoryRepository.findLowStockItems()).stream()
        .map(this::enrichInventoryResponse)
        .collect(Collectors.toList());
  }

  @Override
  public void deleteInventory(UUID id) {
    MaterialInventory inventory = requireInventory(id);

    if (inventory.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
      throw new BusinessException("Cannot delete inventory with remaining quantity");
    }

    inventoryRepository.delete(inventory);
  }

  private void updateInventoryStatus(MaterialInventory inventory) {
    if (inventory.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
      inventory.setStatus(MaterialInventory.InventoryStatus.OUT_OF_STOCK);
    } else if (inventory.getMinimumStockLevel() != null
        && inventory.getAvailableQuantity().compareTo(inventory.getMinimumStockLevel()) <= 0) {
      inventory.setStatus(MaterialInventory.InventoryStatus.LOW_STOCK);
    } else if (inventory.getReservedQuantity().compareTo(BigDecimal.ZERO) > 0) {
      inventory.setStatus(MaterialInventory.InventoryStatus.RESERVED);
    } else {
      inventory.setStatus(MaterialInventory.InventoryStatus.IN_STOCK);
    }
  }

  private MaterialInventoryResponse enrichInventoryResponse(MaterialInventory inventory) {
    MaterialInventoryResponse response = inventoryMapper.toResponse(inventory);

    // Enrich material name
    materialRepository
        .findById(inventory.getMaterialId())
        .ifPresent(material -> response.setMaterialName(material.getName()));

    return response;
  }
}
