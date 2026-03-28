package com.housingplatform.construction.service.impl;

import com.housingplatform.construction.domain.Material;
import com.housingplatform.construction.dto.MaterialRequest;
import com.housingplatform.construction.dto.MaterialResponse;
import com.housingplatform.construction.repository.MaterialRepository;
import com.housingplatform.construction.service.MaterialMapper;
import com.housingplatform.construction.service.MaterialService;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MaterialServiceImpl implements MaterialService {

  private final MaterialRepository materialRepository;
  private final MaterialMapper materialMapper;

  @Override
  public MaterialResponse createMaterial(UUID supplierId, MaterialRequest request) {
    Material material = materialMapper.toEntity(request);
    material.setSupplierId(supplierId);
    material.setStatus(Material.MaterialStatus.AVAILABLE);
    Material saved = materialRepository.save(material);
    return materialMapper.toResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public MaterialResponse getMaterialById(UUID id) {
    Material material =
        materialRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Material", id));
    return materialMapper.toResponse(material);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<MaterialResponse> getAllMaterials(
      UUID supplierId, String category, Pageable pageable) {
    Specification<Material> spec = Specification.where(null);

    if (supplierId != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("supplierId"), supplierId));
    }

    if (category != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), category));
    }

    return materialRepository.findAll(spec, pageable).map(materialMapper::toResponse);
  }

  @Override
  public MaterialResponse updateMaterial(
      UUID supplierId, UUID materialId, MaterialRequest request) {
    Material material =
        materialRepository
            .findById(materialId)
            .orElseThrow(() -> new ResourceNotFoundException("Material", materialId));

    if (!material.getSupplierId().equals(supplierId)) {
      throw new IllegalArgumentException("Material does not belong to the specified supplier");
    }

    materialMapper.updateEntity(material, request);
    Material updated = materialRepository.save(material);
    return materialMapper.toResponse(updated);
  }

  @Override
  public void deleteMaterial(UUID supplierId, UUID materialId) {
    Material material =
        materialRepository
            .findById(materialId)
            .orElseThrow(() -> new ResourceNotFoundException("Material", materialId));

    if (!material.getSupplierId().equals(supplierId)) {
      throw new IllegalArgumentException("Material does not belong to the specified supplier");
    }

    materialRepository.delete(material);
  }
}
