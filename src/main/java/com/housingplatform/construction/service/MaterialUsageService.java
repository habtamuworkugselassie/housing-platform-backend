package com.housingplatform.construction.service;

import com.housingplatform.construction.dto.MaterialUsageRequest;
import com.housingplatform.construction.dto.MaterialUsageResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MaterialUsageService {
  MaterialUsageResponse recordUsage(UUID userId, MaterialUsageRequest request);

  MaterialUsageResponse getUsageById(UUID id);

  List<MaterialUsageResponse> getUsageByProject(UUID projectId);

  List<MaterialUsageResponse> getUsageByPhase(UUID phaseId);

  List<MaterialUsageResponse> getUsageByMaterial(UUID materialId);

  Page<MaterialUsageResponse> getUsageByProject(UUID projectId, Pageable pageable);

  java.math.BigDecimal getTotalUsageByMaterialAndProject(UUID materialId, UUID projectId);

  void deleteUsage(UUID id);
}
