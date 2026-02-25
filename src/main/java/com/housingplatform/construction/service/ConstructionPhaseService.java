package com.housingplatform.construction.service;

import com.housingplatform.construction.domain.ConstructionPhase;
import com.housingplatform.construction.dto.ConstructionPhaseRequest;
import com.housingplatform.construction.dto.ConstructionPhaseResponse;
import java.util.List;
import java.util.UUID;

public interface ConstructionPhaseService {
  ConstructionPhaseResponse createPhase(UUID projectId, ConstructionPhaseRequest request);

  ConstructionPhaseResponse getPhaseById(UUID id);

  List<ConstructionPhaseResponse> getPhasesByProject(UUID projectId);

  ConstructionPhaseResponse updatePhase(
      UUID projectId, UUID phaseId, ConstructionPhaseRequest request);

  ConstructionPhaseResponse updatePhaseStatus(UUID phaseId, ConstructionPhase.PhaseStatus status);

  ConstructionPhaseResponse updatePhaseCompletion(UUID phaseId, Integer completionPercentage);

  void deletePhase(UUID projectId, UUID phaseId);

  void reorderPhases(UUID projectId, List<UUID> phaseIdsInOrder);
}
