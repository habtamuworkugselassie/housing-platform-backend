package com.housingplatform.construction.service;

import com.housingplatform.construction.dto.MaterialRequest;
import com.housingplatform.construction.dto.MaterialResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface MaterialService {
    MaterialResponse createMaterial(UUID supplierId, MaterialRequest request);
    MaterialResponse getMaterialById(UUID id);
    Page<MaterialResponse> getAllMaterials(UUID supplierId, String category, Pageable pageable);
    MaterialResponse updateMaterial(UUID supplierId, UUID materialId, MaterialRequest request);
    void deleteMaterial(UUID supplierId, UUID materialId);
}
