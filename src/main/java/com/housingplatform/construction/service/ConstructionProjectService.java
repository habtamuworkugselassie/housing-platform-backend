package com.housingplatform.construction.service;

import com.housingplatform.construction.domain.ConstructionProject;
import com.housingplatform.construction.dto.ConstructionProjectRequest;
import com.housingplatform.construction.dto.ConstructionProjectResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ConstructionProjectService {
  ConstructionProjectResponse createProject(UUID companyId, ConstructionProjectRequest request);

  ConstructionProjectResponse getProjectById(UUID id);

  Page<ConstructionProjectResponse> getProjectsByCompany(
      UUID companyId, ConstructionProject.ProjectStatus status, Pageable pageable);

  List<ConstructionProjectResponse> getProjectsByProperty(UUID propertyId);

  List<ConstructionProjectResponse> getProjectsByBuilding(UUID buildingId);

  Page<ConstructionProjectResponse> getProjectsByManager(UUID managerId, Pageable pageable);

  ConstructionProjectResponse updateProject(
      UUID companyId, UUID projectId, ConstructionProjectRequest request);

  ConstructionProjectResponse updateProjectStatus(
      UUID projectId, ConstructionProject.ProjectStatus status);

  void deleteProject(UUID companyId, UUID projectId);

  ConstructionProjectResponse calculateProjectCosts(UUID projectId);
}
