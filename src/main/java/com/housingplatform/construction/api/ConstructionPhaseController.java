package com.housingplatform.construction.api;

import com.housingplatform.construction.domain.ConstructionPhase;
import com.housingplatform.construction.dto.ConstructionPhaseRequest;
import com.housingplatform.construction.dto.ConstructionPhaseResponse;
import com.housingplatform.construction.service.ConstructionPhaseService;
import com.housingplatform.shared.security.annotation.AuthActionScope;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/construction-projects/{projectId}/phases")
@Tag(name = "Construction Phases", description = "Construction phase management APIs")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
public class ConstructionPhaseController {

  private final ConstructionPhaseService phaseService;

  @PostMapping
  @AuthActionScope("construction.phases.create")
  @Operation(
      summary = "Create phase",
      description = "Create a new construction phase for a project")
  public ResponseEntity<ConstructionPhaseResponse> createPhase(
      @PathVariable UUID projectId, @Valid @RequestBody ConstructionPhaseRequest request) {
    request.setProjectId(projectId);
    ConstructionPhaseResponse created = phaseService.createPhase(projectId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping
  @Operation(
      summary = "List phases",
      description = "Retrieve all phases for a construction project")
  public ResponseEntity<List<ConstructionPhaseResponse>> getPhases(@PathVariable UUID projectId) {
    List<ConstructionPhaseResponse> phases = phaseService.getPhasesByProject(projectId);
    return ResponseEntity.ok(phases);
  }

  @GetMapping("/{phaseId}")
  @Operation(
      summary = "Get phase by ID",
      description = "Retrieve construction phase information by ID")
  public ResponseEntity<ConstructionPhaseResponse> getPhaseById(
      @PathVariable UUID projectId, @PathVariable UUID phaseId) {
    ConstructionPhaseResponse phase = phaseService.getPhaseById(phaseId);
    return ResponseEntity.ok(phase);
  }

  @PutMapping("/{phaseId}")
  @AuthActionScope("construction.phases.update")
  @Operation(summary = "Update phase", description = "Update construction phase information")
  public ResponseEntity<ConstructionPhaseResponse> updatePhase(
      @PathVariable UUID projectId,
      @PathVariable UUID phaseId,
      @Valid @RequestBody ConstructionPhaseRequest request) {
    request.setProjectId(projectId);
    ConstructionPhaseResponse updated = phaseService.updatePhase(projectId, phaseId, request);
    return ResponseEntity.ok(updated);
  }

  @PutMapping("/{phaseId}/status")
  @AuthActionScope("construction.phases.update")
  @Operation(
      summary = "Update phase status",
      description = "Update the status of a construction phase")
  public ResponseEntity<ConstructionPhaseResponse> updatePhaseStatus(
      @PathVariable UUID projectId,
      @PathVariable UUID phaseId,
      @RequestParam ConstructionPhase.PhaseStatus status) {
    ConstructionPhaseResponse updated = phaseService.updatePhaseStatus(phaseId, status);
    return ResponseEntity.ok(updated);
  }

  @PutMapping("/{phaseId}/completion")
  @AuthActionScope("construction.phases.update")
  @Operation(
      summary = "Update phase completion",
      description = "Update the completion percentage of a construction phase")
  public ResponseEntity<ConstructionPhaseResponse> updatePhaseCompletion(
      @PathVariable UUID projectId,
      @PathVariable UUID phaseId,
      @RequestParam Integer completionPercentage) {
    ConstructionPhaseResponse updated =
        phaseService.updatePhaseCompletion(phaseId, completionPercentage);
    return ResponseEntity.ok(updated);
  }

  @PutMapping("/reorder")
  @AuthActionScope("construction.phases.update")
  @Operation(summary = "Reorder phases", description = "Reorder construction phases for a project")
  public ResponseEntity<Void> reorderPhases(
      @PathVariable UUID projectId, @RequestBody List<UUID> phaseIdsInOrder) {
    phaseService.reorderPhases(projectId, phaseIdsInOrder);
    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/{phaseId}")
  @AuthActionScope("construction.phases.delete")
  @Operation(summary = "Delete phase", description = "Delete a construction phase")
  public ResponseEntity<Void> deletePhase(
      @PathVariable UUID projectId, @PathVariable UUID phaseId) {
    phaseService.deletePhase(projectId, phaseId);
    return ResponseEntity.noContent().build();
  }
}
