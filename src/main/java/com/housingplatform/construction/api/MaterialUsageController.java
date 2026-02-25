package com.housingplatform.construction.api;

import com.housingplatform.construction.dto.MaterialUsageRequest;
import com.housingplatform.construction.dto.MaterialUsageResponse;
import com.housingplatform.construction.service.MaterialUsageService;
import com.housingplatform.shared.security.UserContext;
import com.housingplatform.shared.security.annotation.AuthActionScope;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/material-usage")
@Tag(name = "Material Usage", description = "Material usage tracking APIs")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
public class MaterialUsageController {

  private final MaterialUsageService usageService;

  @PostMapping
  @AuthActionScope("construction.usage.create")
  @Operation(
      summary = "Record material usage",
      description = "Record material usage for a project or phase")
  public ResponseEntity<MaterialUsageResponse> recordUsage(
      @Valid @RequestBody MaterialUsageRequest request) {
    UUID userId = UserContext.getCurrentUserId();
    MaterialUsageResponse created = usageService.recordUsage(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get usage by ID", description = "Retrieve material usage information by ID")
  public ResponseEntity<MaterialUsageResponse> getUsageById(@PathVariable UUID id) {
    MaterialUsageResponse usage = usageService.getUsageById(id);
    return ResponseEntity.ok(usage);
  }

  @GetMapping("/project/{projectId}")
  @Operation(
      summary = "Get usage by project",
      description = "Retrieve all material usage records for a project")
  public ResponseEntity<Page<MaterialUsageResponse>> getUsageByProject(
      @PathVariable UUID projectId,
      @RequestParam(defaultValue = "0") Integer page,
      @RequestParam(defaultValue = "20") Integer size) {
    Pageable pageable = PageRequest.of(page, size);
    Page<MaterialUsageResponse> usage = usageService.getUsageByProject(projectId, pageable);
    return ResponseEntity.ok(usage);
  }

  @GetMapping("/project/{projectId}/all")
  @Operation(
      summary = "Get all usage by project",
      description = "Retrieve all material usage records for a project (no pagination)")
  public ResponseEntity<List<MaterialUsageResponse>> getAllUsageByProject(
      @PathVariable UUID projectId) {
    List<MaterialUsageResponse> usage = usageService.getUsageByProject(projectId);
    return ResponseEntity.ok(usage);
  }

  @GetMapping("/phase/{phaseId}")
  @Operation(
      summary = "Get usage by phase",
      description = "Retrieve all material usage records for a construction phase")
  public ResponseEntity<List<MaterialUsageResponse>> getUsageByPhase(@PathVariable UUID phaseId) {
    List<MaterialUsageResponse> usage = usageService.getUsageByPhase(phaseId);
    return ResponseEntity.ok(usage);
  }

  @GetMapping("/material/{materialId}")
  @Operation(
      summary = "Get usage by material",
      description = "Retrieve all material usage records for a specific material")
  public ResponseEntity<List<MaterialUsageResponse>> getUsageByMaterial(
      @PathVariable UUID materialId) {
    List<MaterialUsageResponse> usage = usageService.getUsageByMaterial(materialId);
    return ResponseEntity.ok(usage);
  }

  @GetMapping("/project/{projectId}/material/{materialId}/total")
  @Operation(
      summary = "Get total usage",
      description = "Get total quantity used for a material in a project")
  public ResponseEntity<java.math.BigDecimal> getTotalUsage(
      @PathVariable UUID projectId, @PathVariable UUID materialId) {
    java.math.BigDecimal total =
        usageService.getTotalUsageByMaterialAndProject(materialId, projectId);
    return ResponseEntity.ok(total);
  }

  @DeleteMapping("/{id}")
  @AuthActionScope("construction.usage.delete")
  @Operation(
      summary = "Delete usage record",
      description = "Delete a material usage record (reverses inventory and cost adjustments)")
  public ResponseEntity<Void> deleteUsage(@PathVariable UUID id) {
    usageService.deleteUsage(id);
    return ResponseEntity.noContent().build();
  }
}
