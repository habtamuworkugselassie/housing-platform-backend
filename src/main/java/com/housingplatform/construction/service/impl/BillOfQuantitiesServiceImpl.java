package com.housingplatform.construction.service.impl;

import com.housingplatform.construction.domain.BillOfQuantities;
import com.housingplatform.construction.domain.BoQItem;
import com.housingplatform.construction.domain.ConstructionProject;
import com.housingplatform.construction.dto.BillOfQuantitiesRequest;
import com.housingplatform.construction.dto.BillOfQuantitiesResponse;
import com.housingplatform.construction.dto.BoQItemRequest;
import com.housingplatform.construction.repository.BillOfQuantitiesRepository;
import com.housingplatform.construction.service.BillOfQuantitiesMapper;
import com.housingplatform.construction.service.BillOfQuantitiesService;
import com.housingplatform.construction.service.ConstructionAccessGuard;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class BillOfQuantitiesServiceImpl implements BillOfQuantitiesService {

  private final BillOfQuantitiesRepository boqRepository;
  private final BillOfQuantitiesMapper boqMapper;
  private final ConstructionAccessGuard accessGuard;

  /** Loads a BoQ the caller may access (see {@link ConstructionAccessGuard#canAccessBoQ}). */
  private BillOfQuantities requireBoQ(UUID id) {
    return boqRepository
        .findById(id)
        .filter(accessGuard::canAccessBoQ)
        .orElseThrow(() -> new ResourceNotFoundException("BillOfQuantities", id));
  }

  /**
   * Validates the project/property a BoQ is being attached to and binds the project association.
   * {@code projectId} on the entity is a read-only mirror of the {@code project} join column, so
   * the association must be set for the link to be persisted at all.
   */
  private void bindOwner(BillOfQuantities boq, UUID projectId, UUID propertyId) {
    ConstructionProject project = projectId != null ? accessGuard.requireProject(projectId) : null;
    boq.setProject(project);
    boq.setProjectId(projectId);
    if (propertyId != null
        && !propertyId.equals(boq.getId() == null ? null : boq.getPropertyId())
        && !accessGuard.canAccessProperty(propertyId)) {
      throw new ResourceNotFoundException("Property", propertyId);
    }
    boq.setPropertyId(propertyId);
    // A BoQ with neither a project nor a property has no owner to scope it by; only admins may
    // create such free-standing BoQs.
    if (projectId == null && propertyId == null && !accessGuard.isAdmin()) {
      throw new BusinessException("A bill of quantities must reference a project or a property");
    }
  }

  @Override
  public BillOfQuantitiesResponse createBoQ(BillOfQuantitiesRequest request) {
    BillOfQuantities boq = boqMapper.toEntity(request);
    bindOwner(boq, request.getProjectId(), request.getPropertyId());
    boq.setStatus(BillOfQuantities.BoQStatus.DRAFT);

    // Calculate total cost
    BigDecimal totalCost =
        request.getItems().stream()
            .map(item -> item.getQuantity().multiply(item.getUnitPrice()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    boq.setTotalEstimatedCost(totalCost);

    // Create items
    for (int i = 0; i < request.getItems().size(); i++) {
      BoQItemRequest itemRequest = request.getItems().get(i);
      BoQItem item = boqMapper.toItemEntity(itemRequest);
      item.setBillOfQuantities(boq);
      item.setTotalPrice(itemRequest.getQuantity().multiply(itemRequest.getUnitPrice()));
      item.setSequence(i + 1);
      boq.getItems().add(item);
    }

    BillOfQuantities saved = boqRepository.save(boq);
    return boqMapper.toResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public BillOfQuantitiesResponse getBoQById(UUID id) {
    return boqMapper.toResponse(requireBoQ(id));
  }

  @Override
  @Transactional(readOnly = true)
  public List<BillOfQuantitiesResponse> getAllBoQs(UUID propertyId, UUID projectId) {
    List<BillOfQuantities> boqs;

    if (propertyId != null) {
      boqs =
          boqRepository.findByPropertyId(propertyId).stream()
              .filter(accessGuard::canAccessBoQ)
              .collect(Collectors.toList());
    } else if (projectId != null) {
      accessGuard.requireProject(projectId);
      boqs = boqRepository.findByProjectId(projectId);
    } else if (accessGuard.isAdmin()) {
      boqs = boqRepository.findAll();
    } else {
      // Unfiltered list for non-admins: BoQs of projects and properties the caller can access.
      Map<UUID, BillOfQuantities> visible = new LinkedHashMap<>();
      Set<UUID> projectIds = accessGuard.accessibleProjectIds();
      if (!projectIds.isEmpty()) {
        boqRepository.findByProjectIdIn(projectIds).forEach(b -> visible.put(b.getId(), b));
      }
      Set<UUID> propertyIds = accessGuard.accessiblePropertyIds();
      if (!propertyIds.isEmpty()) {
        boqRepository.findByPropertyIdIn(propertyIds).forEach(b -> visible.put(b.getId(), b));
      }
      boqs = new ArrayList<>(visible.values());
    }

    return boqs.stream().map(boqMapper::toResponse).collect(Collectors.toList());
  }

  @Override
  public BillOfQuantitiesResponse updateBoQ(UUID id, BillOfQuantitiesRequest request) {
    BillOfQuantities boq = requireBoQ(id);

    // Clear existing items
    boq.getItems().clear();

    // Update basic fields
    boq.setName(request.getName());
    boq.setDescription(request.getDescription());
    bindOwner(boq, request.getProjectId(), request.getPropertyId());

    // Recalculate total and add items
    BigDecimal totalCost = BigDecimal.ZERO;
    for (int i = 0; i < request.getItems().size(); i++) {
      BoQItemRequest itemRequest = request.getItems().get(i);
      BoQItem item = boqMapper.toItemEntity(itemRequest);
      item.setBillOfQuantities(boq);
      item.setTotalPrice(itemRequest.getQuantity().multiply(itemRequest.getUnitPrice()));
      item.setSequence(i + 1);
      boq.getItems().add(item);
      totalCost = totalCost.add(item.getTotalPrice());
    }
    boq.setTotalEstimatedCost(totalCost);

    BillOfQuantities updated = boqRepository.save(boq);
    return boqMapper.toResponse(updated);
  }

  @Override
  public void deleteBoQ(UUID id) {
    boqRepository.delete(requireBoQ(id));
  }
}
