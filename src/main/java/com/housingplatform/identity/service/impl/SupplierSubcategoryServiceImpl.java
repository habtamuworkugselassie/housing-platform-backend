package com.housingplatform.identity.service.impl;

import com.housingplatform.identity.domain.SupplierSubcategory;
import com.housingplatform.identity.dto.SupplierSubcategoryRequest;
import com.housingplatform.identity.dto.SupplierSubcategoryResponse;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.SupplierSubcategoryRepository;
import com.housingplatform.identity.service.SupplierSubcategoryService;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SupplierSubcategoryServiceImpl implements SupplierSubcategoryService {

  private final SupplierSubcategoryRepository supplierSubcategoryRepository;
  private final OrganizationRepository organizationRepository;

  @Override
  @Transactional(readOnly = true)
  public List<SupplierSubcategoryResponse> listActive() {
    return supplierSubcategoryRepository.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
        .map(this::toResponse)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<SupplierSubcategoryResponse> listAllForAdmin() {
    return supplierSubcategoryRepository.findAllByOrderBySortOrderAscNameAsc().stream()
        .map(this::toResponse)
        .collect(Collectors.toList());
  }

  @Override
  public SupplierSubcategoryResponse create(SupplierSubcategoryRequest request) {
    String slug = resolveSlug(request.getSlug(), request.getName(), null);
    int sortOrder =
        request.getSortOrder() != null ? request.getSortOrder() : defaultNextSortOrder();
    boolean active = request.getActive() == null || request.getActive();
    SupplierSubcategory entity =
        SupplierSubcategory.builder()
            .name(request.getName().trim())
            .slug(slug)
            .sortOrder(sortOrder)
            .active(active)
            .build();
    return toResponse(supplierSubcategoryRepository.save(entity));
  }

  @Override
  public SupplierSubcategoryResponse update(UUID id, SupplierSubcategoryRequest request) {
    SupplierSubcategory entity =
        supplierSubcategoryRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("SupplierSubcategory", id));
    if (request.getName() != null && !request.getName().isBlank()) {
      entity.setName(request.getName().trim());
    }
    if (request.getSlug() != null && !request.getSlug().isBlank()) {
      String newSlug = normalizeSlug(request.getSlug());
      if (!newSlug.equalsIgnoreCase(entity.getSlug()) && slugTaken(newSlug, id)) {
        throw new BusinessException("A subcategory with this slug already exists");
      }
      entity.setSlug(newSlug);
    }
    if (request.getSortOrder() != null) {
      entity.setSortOrder(request.getSortOrder());
    }
    if (request.getActive() != null) {
      entity.setActive(request.getActive());
    }
    return toResponse(supplierSubcategoryRepository.save(entity));
  }

  @Override
  public void delete(UUID id) {
    if (!supplierSubcategoryRepository.existsById(id)) {
      throw new ResourceNotFoundException("SupplierSubcategory", id);
    }
    if (organizationRepository.countOrganizationsUsingSubcategory(id) > 0) {
      throw new BusinessException(
          "This subcategory is assigned to one or more suppliers. Remove assignments before"
              + " deleting.");
    }
    supplierSubcategoryRepository.deleteById(id);
  }

  private int defaultNextSortOrder() {
    return supplierSubcategoryRepository.findAllByOrderBySortOrderAscNameAsc().stream()
            .mapToInt(SupplierSubcategory::getSortOrder)
            .max()
            .orElse(0)
        + 10;
  }

  private String resolveSlug(String requestedSlug, String name, UUID excludeId) {
    String base =
        requestedSlug != null && !requestedSlug.isBlank()
            ? normalizeSlug(requestedSlug)
            : normalizeSlug(name);
    if (base.isEmpty()) {
      base = "SUBCATEGORY";
    }
    String candidate = base;
    int n = 1;
    while (slugTaken(candidate, excludeId)) {
      candidate = base + "_" + n++;
    }
    return candidate;
  }

  private boolean slugTaken(String slug, UUID excludeId) {
    for (SupplierSubcategory s :
        supplierSubcategoryRepository.findAllByOrderBySortOrderAscNameAsc()) {
      if (excludeId != null && excludeId.equals(s.getId())) {
        continue;
      }
      if (slug.equalsIgnoreCase(s.getSlug())) {
        return true;
      }
    }
    return false;
  }

  private static String normalizeSlug(String raw) {
    if (raw == null) {
      return "";
    }
    String s = raw.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_");
    s = s.replaceAll("^_+|_+$", "");
    return s.isEmpty() ? "" : s;
  }

  private SupplierSubcategoryResponse toResponse(SupplierSubcategory s) {
    return SupplierSubcategoryResponse.builder()
        .id(s.getId())
        .name(s.getName())
        .slug(s.getSlug())
        .sortOrder(s.getSortOrder())
        .active(s.isActive())
        .build();
  }
}
