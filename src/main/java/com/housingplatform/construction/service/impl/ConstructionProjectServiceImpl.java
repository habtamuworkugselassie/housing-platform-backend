package com.housingplatform.construction.service.impl;

import com.housingplatform.construction.domain.ConstructionPhase;
import com.housingplatform.construction.domain.ConstructionProject;
import com.housingplatform.construction.dto.ConstructionPhaseResponse;
import com.housingplatform.construction.dto.ConstructionProjectRequest;
import com.housingplatform.construction.dto.ConstructionProjectResponse;
import com.housingplatform.construction.repository.ConstructionPhaseRepository;
import com.housingplatform.construction.repository.ConstructionProjectRepository;
import com.housingplatform.construction.service.ConstructionAccessGuard;
import com.housingplatform.construction.service.ConstructionPhaseMapper;
import com.housingplatform.construction.service.ConstructionProjectMapper;
import com.housingplatform.construction.service.ConstructionProjectService;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
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
public class ConstructionProjectServiceImpl implements ConstructionProjectService {

  private final ConstructionProjectRepository projectRepository;
  private final ConstructionPhaseRepository phaseRepository;
  private final ConstructionProjectMapper projectMapper;
  private final ConstructionPhaseMapper phaseMapper;
  private final OrganizationRepository organizationRepository;
  private final ConstructionAccessGuard accessGuard;

  @Override
  public ConstructionProjectResponse createProject(
      UUID companyId, ConstructionProjectRequest request) {
    // Validate organization exists
    organizationRepository
        .findById(companyId)
        .orElseThrow(() -> new ResourceNotFoundException("Organization", companyId));

    ConstructionProject project = projectMapper.toEntity(request);
    project.setRealEstateCompanyId(companyId);
    project.setStatus(ConstructionProject.ProjectStatus.PLANNING);

    if (request.getCurrency() == null) {
      project.setCurrency(com.housingplatform.shared.domain.Currency.ETB);
    }

    ConstructionProject saved = projectRepository.save(project);
    return enrichProjectResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public ConstructionProjectResponse getProjectById(UUID id) {
    return enrichProjectResponse(accessGuard.requireProject(id));
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ConstructionProjectResponse> getProjectsByCompany(
      UUID companyId, ConstructionProject.ProjectStatus status, Pageable pageable) {
    Page<ConstructionProject> projects;
    if (status != null) {
      projects = projectRepository.findByCompanyAndStatus(companyId, status, pageable);
    } else {
      projects = projectRepository.findByRealEstateCompanyId(companyId, pageable);
    }
    return projects.map(this::enrichProjectResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ConstructionProjectResponse> getProjectsByProperty(UUID propertyId) {
    // Scoped: only projects the caller can access (admins see all).
    return projectRepository.findByPropertyId(propertyId).stream()
        .filter(accessGuard::canAccessProject)
        .map(this::enrichProjectResponse)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<ConstructionProjectResponse> getProjectsByBuilding(UUID buildingId) {
    // Scoped: only projects the caller can access (admins see all).
    return projectRepository.findByBuildingId(buildingId).stream()
        .filter(accessGuard::canAccessProject)
        .map(this::enrichProjectResponse)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ConstructionProjectResponse> getProjectsByManager(UUID managerId, Pageable pageable) {
    // Admins and the manager themself see every project they manage; anyone else only sees that
    // manager's projects owned by the caller's own organization.
    if (accessGuard.isAdmin() || managerId.equals(accessGuard.currentUserId())) {
      return projectRepository
          .findByProjectManagerId(managerId, pageable)
          .map(this::enrichProjectResponse);
    }
    return accessGuard
        .currentOrganizationId()
        .map(
            org ->
                projectRepository
                    .findByProjectManagerIdAndRealEstateCompanyId(managerId, org, pageable)
                    .map(this::enrichProjectResponse))
        .orElseGet(() -> Page.empty(pageable));
  }

  @Override
  public ConstructionProjectResponse updateProject(
      UUID companyId, UUID projectId, ConstructionProjectRequest request) {
    ConstructionProject project = accessGuard.requireProject(projectId);

    // Editing project details stays with the owning organization (or an admin), as before.
    if (!accessGuard.isAdmin() && !project.getRealEstateCompanyId().equals(companyId)) {
      throw new BusinessException("Project does not belong to the specified company");
    }

    UUID owningCompanyId = project.getRealEstateCompanyId();
    projectMapper.updateEntity(project, request);
    // The request carries realEstateCompanyId; only admins may move a project to another company.
    if (!accessGuard.isAdmin()) {
      project.setRealEstateCompanyId(owningCompanyId);
    }
    ConstructionProject updated = projectRepository.save(project);
    return enrichProjectResponse(updated);
  }

  @Override
  public ConstructionProjectResponse updateProjectStatus(
      UUID projectId, ConstructionProject.ProjectStatus status) {
    ConstructionProject project = accessGuard.requireProject(projectId);

    project.setStatus(status);

    if (status == ConstructionProject.ProjectStatus.COMPLETED
        && project.getActualEndDate() == null) {
      project.setActualEndDate(java.time.LocalDate.now());
    }

    ConstructionProject updated = projectRepository.save(project);
    return enrichProjectResponse(updated);
  }

  @Override
  public void deleteProject(UUID companyId, UUID projectId) {
    ConstructionProject project = accessGuard.requireProject(projectId);

    // Deleting a project is reserved for the owning organization (or an admin); managers and
    // team members can work on it but not remove it.
    if (!accessGuard.isAdmin() && !project.getRealEstateCompanyId().equals(companyId)) {
      throw new BusinessException("Project does not belong to the specified company");
    }

    projectRepository.delete(project);
  }

  @Override
  public ConstructionProjectResponse calculateProjectCosts(UUID projectId) {
    ConstructionProject project = accessGuard.requireProject(projectId);

    // Calculate total cost from phases
    List<ConstructionPhase> phases = phaseRepository.findByProjectIdOrderBySequenceAsc(projectId);
    BigDecimal totalCost =
        phases.stream()
            .filter(p -> p.getActualCost() != null)
            .map(ConstructionPhase::getActualCost)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    project.setTotalCost(totalCost);
    ConstructionProject updated = projectRepository.save(project);
    return enrichProjectResponse(updated);
  }

  private ConstructionProjectResponse enrichProjectResponse(ConstructionProject project) {
    ConstructionProjectResponse response = projectMapper.toResponse(project);

    // Enrich with organization name
    organizationRepository
        .findById(project.getRealEstateCompanyId())
        .ifPresent(org -> response.setRealEstateCompanyName(org.getName()));

    // Enrich with phases
    List<ConstructionPhase> phases =
        phaseRepository.findByProjectIdOrderBySequenceAsc(project.getId());
    List<ConstructionPhaseResponse> phaseResponses =
        phases.stream().map(phaseMapper::toResponse).collect(Collectors.toList());
    response.setPhases(phaseResponses);
    response.setTotalPhases(phases.size());

    // Calculate completion percentage
    long completedPhases =
        phases.stream()
            .filter(p -> p.getStatus() == ConstructionPhase.PhaseStatus.COMPLETED)
            .count();
    response.setCompletedPhases((int) completedPhases);

    if (!phases.isEmpty()) {
      int overallCompletion =
          phases.stream()
                  .filter(p -> p.getCompletionPercentage() != null)
                  .mapToInt(ConstructionPhase::getCompletionPercentage)
                  .sum()
              / phases.size();
      response.setOverallCompletionPercentage(overallCompletion);
    }

    return response;
  }
}
