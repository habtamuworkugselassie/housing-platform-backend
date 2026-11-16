package com.housingplatform.construction.service.impl;

import com.housingplatform.construction.domain.ConstructionPhase;
import com.housingplatform.construction.domain.ConstructionProject;
import com.housingplatform.construction.domain.MaterialInventory;
import com.housingplatform.construction.domain.MaterialUsage;
import com.housingplatform.construction.dto.MaterialUsageRequest;
import com.housingplatform.construction.dto.MaterialUsageResponse;
import com.housingplatform.construction.repository.ConstructionPhaseRepository;
import com.housingplatform.construction.repository.ConstructionProjectRepository;
import com.housingplatform.construction.repository.MaterialInventoryRepository;
import com.housingplatform.construction.repository.MaterialOrderRepository;
import com.housingplatform.construction.repository.MaterialRepository;
import com.housingplatform.construction.repository.MaterialUsageRepository;
import com.housingplatform.construction.service.ConstructionAccessGuard;
import com.housingplatform.construction.service.MaterialUsageMapper;
import com.housingplatform.construction.service.MaterialUsageService;
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
public class MaterialUsageServiceImpl implements MaterialUsageService {

  private final MaterialUsageRepository usageRepository;
  private final MaterialUsageMapper usageMapper;
  private final ConstructionProjectRepository projectRepository;
  private final ConstructionPhaseRepository phaseRepository;
  private final MaterialRepository materialRepository;
  private final MaterialInventoryRepository inventoryRepository;
  private final MaterialOrderRepository orderRepository;
  private final ConstructionAccessGuard accessGuard;

  /** Loads a usage record whose project the caller can access, or throws not-found. */
  private MaterialUsage requireUsage(UUID id) {
    MaterialUsage usage =
        usageRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Material Usage", id));
    accessGuard.checkProjectId(usage.getProjectId(), "Material Usage", id);
    return usage;
  }

  @Override
  public MaterialUsageResponse recordUsage(UUID userId, MaterialUsageRequest request) {
    // Validate project exists
    ConstructionProject project = accessGuard.requireProject(request.getProjectId());

    // Validate phase if provided
    ConstructionPhase phase = null;
    if (request.getPhaseId() != null) {
      phase =
          phaseRepository
              .findById(request.getPhaseId())
              .orElseThrow(
                  () -> new ResourceNotFoundException("Construction Phase", request.getPhaseId()));
      if (!phase.getProject().getId().equals(request.getProjectId())) {
        throw new BusinessException("Phase does not belong to the specified project");
      }
    }

    // Validate material exists
    materialRepository
        .findById(request.getMaterialId())
        .orElseThrow(() -> new ResourceNotFoundException("Material", request.getMaterialId()));

    // Validate inventory if provided
    MaterialInventory inventory = null;
    if (request.getInventoryId() != null) {
      inventory =
          inventoryRepository
              .findById(request.getInventoryId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Material Inventory", request.getInventoryId()));
      // Must not be able to draw down stock the caller cannot see (e.g. another company's).
      accessGuard.checkInventory(inventory);

      // Check if enough quantity is available
      if (inventory.getAvailableQuantity().compareTo(request.getQuantity()) < 0) {
        throw new BusinessException(
            "Insufficient inventory. Available: "
                + inventory.getAvailableQuantity()
                + ", Requested: "
                + request.getQuantity());
      }

      // Adjust inventory
      inventory.setQuantity(inventory.getQuantity().subtract(request.getQuantity()));
      inventory.setAvailableQuantity(
          inventory.getAvailableQuantity().subtract(request.getQuantity()));
      inventoryRepository.save(inventory);
    }

    // Validate order if provided
    if (request.getOrderId() != null) {
      orderRepository
          .findById(request.getOrderId())
          .filter(accessGuard::canAccessOrder)
          .orElseThrow(() -> new ResourceNotFoundException("Material Order", request.getOrderId()));
    }

    MaterialUsage usage = usageMapper.toEntity(request);
    usage.setUsedBy(userId);

    // Calculate total cost
    BigDecimal unitCost = request.getUnitCost();
    if (unitCost == null && inventory != null && inventory.getUnitCost() != null) {
      unitCost = inventory.getUnitCost();
    }

    if (unitCost != null) {
      usage.setUnitCost(unitCost);
      usage.setTotalCost(request.getQuantity().multiply(unitCost));
    }

    MaterialUsage saved = usageRepository.save(usage);

    // Update phase actual cost if phase is provided
    if (phase != null && usage.getTotalCost() != null) {
      BigDecimal currentCost =
          phase.getActualCost() != null ? phase.getActualCost() : BigDecimal.ZERO;
      phase.setActualCost(currentCost.add(usage.getTotalCost()));
      phaseRepository.save(phase);
    }

    // Update project total cost
    if (usage.getTotalCost() != null) {
      BigDecimal currentCost =
          project.getTotalCost() != null ? project.getTotalCost() : BigDecimal.ZERO;
      project.setTotalCost(currentCost.add(usage.getTotalCost()));
      projectRepository.save(project);
    }

    return enrichUsageResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public MaterialUsageResponse getUsageById(UUID id) {
    return enrichUsageResponse(requireUsage(id));
  }

  @Override
  @Transactional(readOnly = true)
  public List<MaterialUsageResponse> getUsageByProject(UUID projectId) {
    accessGuard.requireProject(projectId);
    return usageRepository.findByProjectId(projectId).stream()
        .map(this::enrichUsageResponse)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<MaterialUsageResponse> getUsageByPhase(UUID phaseId) {
    phaseRepository
        .findById(phaseId)
        .ifPresent(
            phase -> accessGuard.checkProject(phase.getProject(), "Construction Phase", phaseId));
    return usageRepository.findByPhaseId(phaseId).stream()
        .map(this::enrichUsageResponse)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<MaterialUsageResponse> getUsageByMaterial(UUID materialId) {
    // Materials are a shared catalog, so usage of one spans many companies: non-admins only get
    // records from projects they can access.
    List<MaterialUsage> usages = usageRepository.findByMaterialId(materialId);
    if (!accessGuard.isAdmin()) {
      Set<UUID> projectIds = accessGuard.accessibleProjectIds();
      usages = usages.stream().filter(u -> projectIds.contains(u.getProjectId())).toList();
    }
    return usages.stream().map(this::enrichUsageResponse).collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public Page<MaterialUsageResponse> getUsageByProject(UUID projectId, Pageable pageable) {
    accessGuard.requireProject(projectId);
    return usageRepository.findByProjectId(projectId, pageable).map(this::enrichUsageResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public BigDecimal getTotalUsageByMaterialAndProject(UUID materialId, UUID projectId) {
    accessGuard.requireProject(projectId);
    BigDecimal total = usageRepository.getTotalUsageByMaterialAndProject(materialId, projectId);
    return total != null ? total : BigDecimal.ZERO;
  }

  @Override
  public void deleteUsage(UUID id) {
    MaterialUsage usage = requireUsage(id);

    // Reverse inventory adjustment if applicable
    if (usage.getInventoryId() != null) {
      MaterialInventory inventory =
          inventoryRepository
              .findById(usage.getInventoryId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException("Material Inventory", usage.getInventoryId()));
      inventory.setQuantity(inventory.getQuantity().add(usage.getQuantity()));
      inventory.setAvailableQuantity(inventory.getAvailableQuantity().add(usage.getQuantity()));
      inventoryRepository.save(inventory);
    }

    // Reverse cost updates
    if (usage.getTotalCost() != null) {
      ConstructionProject project =
          projectRepository
              .findById(usage.getProjectId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException("Construction Project", usage.getProjectId()));
      project.setTotalCost(project.getTotalCost().subtract(usage.getTotalCost()));
      projectRepository.save(project);

      if (usage.getPhaseId() != null) {
        ConstructionPhase phase =
            phaseRepository
                .findById(usage.getPhaseId())
                .orElseThrow(
                    () -> new ResourceNotFoundException("Construction Phase", usage.getPhaseId()));
        phase.setActualCost(phase.getActualCost().subtract(usage.getTotalCost()));
        phaseRepository.save(phase);
      }
    }

    usageRepository.delete(usage);
  }

  private MaterialUsageResponse enrichUsageResponse(MaterialUsage usage) {
    MaterialUsageResponse response = usageMapper.toResponse(usage);

    // Enrich project name
    projectRepository
        .findById(usage.getProjectId())
        .ifPresent(project -> response.setProjectName(project.getName()));

    // Enrich phase name
    if (usage.getPhaseId() != null) {
      phaseRepository
          .findById(usage.getPhaseId())
          .ifPresent(phase -> response.setPhaseName(phase.getName()));
    }

    // Enrich material name
    materialRepository
        .findById(usage.getMaterialId())
        .ifPresent(material -> response.setMaterialName(material.getName()));

    // Enrich order number
    if (usage.getOrderId() != null) {
      orderRepository
          .findById(usage.getOrderId())
          .ifPresent(order -> response.setOrderNumber(order.getOrderNumber()));
    }

    return response;
  }
}
