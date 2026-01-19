package com.housingplatform.construction.api;

import com.housingplatform.construction.dto.ConstructionProjectRequest;
import com.housingplatform.construction.dto.ConstructionProjectResponse;
import com.housingplatform.construction.domain.ConstructionProject;
import com.housingplatform.construction.service.ConstructionProjectService;
import com.housingplatform.shared.security.annotation.AuthActionScope;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import com.housingplatform.shared.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/construction-projects")
@Tag(name = "Construction Projects", description = "Construction project management APIs")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
public class ConstructionProjectController {
    
    private final ConstructionProjectService projectService;
    
    @PostMapping
    @AuthActionScope("construction.projects.create")
    @Operation(summary = "Create construction project", description = "Create a new construction project")
    public ResponseEntity<ConstructionProjectResponse> createProject(@Valid @RequestBody ConstructionProjectRequest request) {
        UUID companyId = UserContext.getCurrentUserOrganizationId()
                .orElseThrow(() -> new IllegalStateException("User must be associated with an organization"));
        ConstructionProjectResponse created = projectService.createProject(companyId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get project by ID", description = "Retrieve construction project information by ID")
    public ResponseEntity<ConstructionProjectResponse> getProjectById(@PathVariable UUID id) {
        ConstructionProjectResponse project = projectService.getProjectById(id);
        return ResponseEntity.ok(project);
    }
    
    @GetMapping
    @Operation(summary = "List projects", description = "Retrieve all construction projects for the current user's organization")
    public ResponseEntity<Page<ConstructionProjectResponse>> getProjects(
            @RequestParam(required = false) ConstructionProject.ProjectStatus status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        UUID companyId = UserContext.getCurrentUserOrganizationId()
                .orElseThrow(() -> new IllegalStateException("User must be associated with an organization"));
        Pageable pageable = PageRequest.of(page, size);
        Page<ConstructionProjectResponse> projects = projectService.getProjectsByCompany(companyId, status, pageable);
        return ResponseEntity.ok(projects);
    }
    
    @GetMapping("/property/{propertyId}")
    @Operation(summary = "Get projects by property", description = "Retrieve all construction projects for a specific property")
    public ResponseEntity<List<ConstructionProjectResponse>> getProjectsByProperty(@PathVariable UUID propertyId) {
        List<ConstructionProjectResponse> projects = projectService.getProjectsByProperty(propertyId);
        return ResponseEntity.ok(projects);
    }
    
    @GetMapping("/building/{buildingId}")
    @Operation(summary = "Get projects by building", description = "Retrieve all construction projects for a specific building")
    public ResponseEntity<List<ConstructionProjectResponse>> getProjectsByBuilding(@PathVariable UUID buildingId) {
        List<ConstructionProjectResponse> projects = projectService.getProjectsByBuilding(buildingId);
        return ResponseEntity.ok(projects);
    }
    
    @GetMapping("/manager/{managerId}")
    @Operation(summary = "Get projects by manager", description = "Retrieve all construction projects managed by a specific user")
    public ResponseEntity<Page<ConstructionProjectResponse>> getProjectsByManager(
            @PathVariable UUID managerId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ConstructionProjectResponse> projects = projectService.getProjectsByManager(managerId, pageable);
        return ResponseEntity.ok(projects);
    }
    
    @PutMapping("/{id}")
    @AuthActionScope("construction.projects.update")
    @Operation(summary = "Update project", description = "Update construction project information")
    public ResponseEntity<ConstructionProjectResponse> updateProject(
            @PathVariable UUID id,
            @Valid @RequestBody ConstructionProjectRequest request) {
        UUID companyId = UserContext.getCurrentUserOrganizationId()
                .orElseThrow(() -> new IllegalStateException("User must be associated with an organization"));
        ConstructionProjectResponse updated = projectService.updateProject(companyId, id, request);
        return ResponseEntity.ok(updated);
    }
    
    @PutMapping("/{id}/status")
    @AuthActionScope("construction.projects.update")
    @Operation(summary = "Update project status", description = "Update the status of a construction project")
    public ResponseEntity<ConstructionProjectResponse> updateProjectStatus(
            @PathVariable UUID id,
            @RequestParam ConstructionProject.ProjectStatus status) {
        ConstructionProjectResponse updated = projectService.updateProjectStatus(id, status);
        return ResponseEntity.ok(updated);
    }
    
    @PostMapping("/{id}/calculate-costs")
    @AuthActionScope("construction.projects.update")
    @Operation(summary = "Calculate project costs", description = "Recalculate total costs for a construction project")
    public ResponseEntity<ConstructionProjectResponse> calculateProjectCosts(@PathVariable UUID id) {
        ConstructionProjectResponse updated = projectService.calculateProjectCosts(id);
        return ResponseEntity.ok(updated);
    }
    
    @DeleteMapping("/{id}")
    @AuthActionScope("construction.projects.delete")
    @Operation(summary = "Delete project", description = "Delete a construction project")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
        UUID companyId = UserContext.getCurrentUserOrganizationId()
                .orElseThrow(() -> new IllegalStateException("User must be associated with an organization"));
        projectService.deleteProject(companyId, id);
        return ResponseEntity.noContent().build();
    }
}
