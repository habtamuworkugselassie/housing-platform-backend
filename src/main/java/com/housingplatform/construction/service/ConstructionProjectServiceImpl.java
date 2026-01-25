package com.housingplatform.construction.service;

import com.housingplatform.construction.domain.ConstructionPhase;
import com.housingplatform.construction.domain.ConstructionProject;
import com.housingplatform.construction.dto.ConstructionProjectRequest;
import com.housingplatform.construction.dto.ConstructionProjectResponse;
import com.housingplatform.construction.dto.ConstructionPhaseResponse;
import com.housingplatform.construction.repository.ConstructionPhaseRepository;
import com.housingplatform.construction.repository.ConstructionProjectRepository;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ConstructionProjectServiceImpl implements ConstructionProjectService {
    
    private final ConstructionProjectRepository projectRepository;
    private final ConstructionPhaseRepository phaseRepository;
    private final ConstructionProjectMapper projectMapper;
    private final ConstructionPhaseMapper phaseMapper;
    private final OrganizationRepository organizationRepository;
    
    @Override
    public ConstructionProjectResponse createProject(UUID companyId, ConstructionProjectRequest request) {
        // Validate organization exists
        organizationRepository.findById(companyId)
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
        ConstructionProject project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Construction Project", id));
        return enrichProjectResponse(project);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<ConstructionProjectResponse> getProjectsByCompany(UUID companyId, ConstructionProject.ProjectStatus status, Pageable pageable) {
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
        return projectRepository.findByPropertyId(propertyId)
                .stream()
                .map(this::enrichProjectResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<ConstructionProjectResponse> getProjectsByBuilding(UUID buildingId) {
        return projectRepository.findByBuildingId(buildingId)
                .stream()
                .map(this::enrichProjectResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<ConstructionProjectResponse> getProjectsByManager(UUID managerId, Pageable pageable) {
        return projectRepository.findByProjectManagerId(managerId, pageable)
                .map(this::enrichProjectResponse);
    }
    
    @Override
    public ConstructionProjectResponse updateProject(UUID companyId, UUID projectId, ConstructionProjectRequest request) {
        ConstructionProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Construction Project", projectId));
        
        // Skip ownership check if admin
        if (!com.housingplatform.shared.security.UserContext.isAdmin() && 
            !project.getRealEstateCompanyId().equals(companyId)) {
            throw new BusinessException("Project does not belong to the specified company");
        }
        
        projectMapper.updateEntity(project, request);
        ConstructionProject updated = projectRepository.save(project);
        return enrichProjectResponse(updated);
    }
    
    @Override
    public ConstructionProjectResponse updateProjectStatus(UUID projectId, ConstructionProject.ProjectStatus status) {
        ConstructionProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Construction Project", projectId));
        
        project.setStatus(status);
        
        if (status == ConstructionProject.ProjectStatus.COMPLETED && project.getActualEndDate() == null) {
            project.setActualEndDate(java.time.LocalDate.now());
        }
        
        ConstructionProject updated = projectRepository.save(project);
        return enrichProjectResponse(updated);
    }
    
    @Override
    public void deleteProject(UUID companyId, UUID projectId) {
        ConstructionProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Construction Project", projectId));
        
        // Skip ownership check if admin
        if (!com.housingplatform.shared.security.UserContext.isAdmin() && 
            !project.getRealEstateCompanyId().equals(companyId)) {
            throw new BusinessException("Project does not belong to the specified company");
        }
        
        projectRepository.delete(project);
    }
    
    @Override
    public ConstructionProjectResponse calculateProjectCosts(UUID projectId) {
        ConstructionProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Construction Project", projectId));
        
        // Calculate total cost from phases
        List<ConstructionPhase> phases = phaseRepository.findByProjectIdOrderBySequenceAsc(projectId);
        BigDecimal totalCost = phases.stream()
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
        organizationRepository.findById(project.getRealEstateCompanyId())
                .ifPresent(org -> response.setRealEstateCompanyName(org.getName()));
        
        // Enrich with phases
        List<ConstructionPhase> phases = phaseRepository.findByProjectIdOrderBySequenceAsc(project.getId());
        List<ConstructionPhaseResponse> phaseResponses = phases.stream()
                .map(phaseMapper::toResponse)
                .collect(Collectors.toList());
        response.setPhases(phaseResponses);
        response.setTotalPhases(phases.size());
        
        // Calculate completion percentage
        long completedPhases = phases.stream()
                .filter(p -> p.getStatus() == ConstructionPhase.PhaseStatus.COMPLETED)
                .count();
        response.setCompletedPhases((int) completedPhases);
        
        if (!phases.isEmpty()) {
            int overallCompletion = phases.stream()
                    .filter(p -> p.getCompletionPercentage() != null)
                    .mapToInt(ConstructionPhase::getCompletionPercentage)
                    .sum() / phases.size();
            response.setOverallCompletionPercentage(overallCompletion);
        }
        
        return response;
    }
}
