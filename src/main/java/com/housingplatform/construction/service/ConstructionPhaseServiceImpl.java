package com.housingplatform.construction.service;

import com.housingplatform.construction.domain.ConstructionPhase;
import com.housingplatform.construction.domain.ConstructionProject;
import com.housingplatform.construction.dto.ConstructionPhaseRequest;
import com.housingplatform.construction.dto.ConstructionPhaseResponse;
import com.housingplatform.construction.repository.ConstructionPhaseRepository;
import com.housingplatform.construction.repository.ConstructionProjectRepository;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ConstructionPhaseServiceImpl implements ConstructionPhaseService {

  private final ConstructionPhaseRepository phaseRepository;
  private final ConstructionProjectRepository projectRepository;
  private final ConstructionPhaseMapper phaseMapper;

  @Override
  public ConstructionPhaseResponse createPhase(UUID projectId, ConstructionPhaseRequest request) {
    ConstructionProject project =
        projectRepository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Construction Project", projectId));

    if (!request.getProjectId().equals(projectId)) {
      throw new BusinessException("Phase project ID does not match the provided project ID");
    }

    ConstructionPhase phase = phaseMapper.toEntity(request);
    phase.setProject(project);
    phase.setStatus(ConstructionPhase.PhaseStatus.NOT_STARTED);
    phase.setCompletionPercentage(0);

    ConstructionPhase saved = phaseRepository.save(phase);
    return phaseMapper.toResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public ConstructionPhaseResponse getPhaseById(UUID id) {
    ConstructionPhase phase =
        phaseRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Construction Phase", id));
    return phaseMapper.toResponse(phase);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ConstructionPhaseResponse> getPhasesByProject(UUID projectId) {
    return phaseRepository.findByProjectIdOrderBySequenceAsc(projectId).stream()
        .map(phaseMapper::toResponse)
        .collect(Collectors.toList());
  }

  @Override
  public ConstructionPhaseResponse updatePhase(
      UUID projectId, UUID phaseId, ConstructionPhaseRequest request) {
    ConstructionPhase phase =
        phaseRepository
            .findById(phaseId)
            .orElseThrow(() -> new ResourceNotFoundException("Construction Phase", phaseId));

    if (!phase.getProject().getId().equals(projectId)) {
      throw new BusinessException("Phase does not belong to the specified project");
    }

    phaseMapper.updateEntity(phase, request);
    ConstructionPhase updated = phaseRepository.save(phase);
    return phaseMapper.toResponse(updated);
  }

  @Override
  public ConstructionPhaseResponse updatePhaseStatus(
      UUID phaseId, ConstructionPhase.PhaseStatus status) {
    ConstructionPhase phase =
        phaseRepository
            .findById(phaseId)
            .orElseThrow(() -> new ResourceNotFoundException("Construction Phase", phaseId));

    phase.setStatus(status);

    if (status == ConstructionPhase.PhaseStatus.COMPLETED) {
      phase.setCompletionPercentage(100);
      if (phase.getActualEndDate() == null) {
        phase.setActualEndDate(LocalDate.now());
      }
    } else if (status == ConstructionPhase.PhaseStatus.IN_PROGRESS
        && phase.getStartDate() == null) {
      phase.setStartDate(LocalDate.now());
    }

    ConstructionPhase updated = phaseRepository.save(phase);
    return phaseMapper.toResponse(updated);
  }

  @Override
  public ConstructionPhaseResponse updatePhaseCompletion(
      UUID phaseId, Integer completionPercentage) {
    if (completionPercentage < 0 || completionPercentage > 100) {
      throw new BusinessException("Completion percentage must be between 0 and 100");
    }

    ConstructionPhase phase =
        phaseRepository
            .findById(phaseId)
            .orElseThrow(() -> new ResourceNotFoundException("Construction Phase", phaseId));

    phase.setCompletionPercentage(completionPercentage);

    if (completionPercentage == 100) {
      phase.setStatus(ConstructionPhase.PhaseStatus.COMPLETED);
      if (phase.getActualEndDate() == null) {
        phase.setActualEndDate(LocalDate.now());
      }
    } else if (completionPercentage > 0
        && phase.getStatus() == ConstructionPhase.PhaseStatus.NOT_STARTED) {
      phase.setStatus(ConstructionPhase.PhaseStatus.IN_PROGRESS);
      if (phase.getStartDate() == null) {
        phase.setStartDate(LocalDate.now());
      }
    }

    ConstructionPhase updated = phaseRepository.save(phase);
    return phaseMapper.toResponse(updated);
  }

  @Override
  public void deletePhase(UUID projectId, UUID phaseId) {
    ConstructionPhase phase =
        phaseRepository
            .findById(phaseId)
            .orElseThrow(() -> new ResourceNotFoundException("Construction Phase", phaseId));

    if (!phase.getProject().getId().equals(projectId)) {
      throw new BusinessException("Phase does not belong to the specified project");
    }

    phaseRepository.delete(phase);
  }

  @Override
  public void reorderPhases(UUID projectId, List<UUID> phaseIdsInOrder) {
    List<ConstructionPhase> phases = phaseRepository.findByProjectIdOrderBySequenceAsc(projectId);

    if (phases.size() != phaseIdsInOrder.size()) {
      throw new BusinessException("Number of phases does not match the provided order list");
    }

    for (int i = 0; i < phaseIdsInOrder.size(); i++) {
      UUID phaseId = phaseIdsInOrder.get(i);
      ConstructionPhase phase =
          phases.stream()
              .filter(p -> p.getId().equals(phaseId))
              .findFirst()
              .orElseThrow(() -> new ResourceNotFoundException("Construction Phase", phaseId));
      phase.setSequence(i + 1);
    }

    phaseRepository.saveAll(phases);
  }
}
