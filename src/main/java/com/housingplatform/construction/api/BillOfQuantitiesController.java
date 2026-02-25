package com.housingplatform.construction.api;

import com.housingplatform.construction.dto.BillOfQuantitiesRequest;
import com.housingplatform.construction.dto.BillOfQuantitiesResponse;
import com.housingplatform.construction.service.BillOfQuantitiesService;
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
@RequestMapping("/api/v1/bills-of-quantities")
@Tag(name = "Bill of Quantities", description = "BoQ management APIs")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
public class BillOfQuantitiesController {

  private final BillOfQuantitiesService boqService;

  @GetMapping
  @Operation(
      summary = "List BoQs",
      description = "Retrieve all bills of quantities with optional filtering")
  public ResponseEntity<List<BillOfQuantitiesResponse>> getAllBoQs(
      @RequestParam(required = false) UUID propertyId,
      @RequestParam(required = false) UUID projectId) {
    List<BillOfQuantitiesResponse> boqs = boqService.getAllBoQs(propertyId, projectId);
    return ResponseEntity.ok(boqs);
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Get BoQ by ID",
      description = "Retrieve bill of quantities information by ID")
  public ResponseEntity<BillOfQuantitiesResponse> getBoQById(@PathVariable UUID id) {
    BillOfQuantitiesResponse boq = boqService.getBoQById(id);
    return ResponseEntity.ok(boq);
  }

  @PostMapping
  @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
  @AuthActionScope("boq.create")
  @Operation(summary = "Create BoQ", description = "Create a new bill of quantities")
  public ResponseEntity<BillOfQuantitiesResponse> createBoQ(
      @Valid @RequestBody BillOfQuantitiesRequest boqRequest) {
    BillOfQuantitiesResponse created = boqService.createBoQ(boqRequest);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PutMapping("/{id}")
  @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
  @AuthActionScope("boq.update")
  @Operation(summary = "Update BoQ", description = "Update bill of quantities information")
  public ResponseEntity<BillOfQuantitiesResponse> updateBoQ(
      @PathVariable UUID id, @Valid @RequestBody BillOfQuantitiesRequest boqRequest) {
    BillOfQuantitiesResponse updated = boqService.updateBoQ(id, boqRequest);
    return ResponseEntity.ok(updated);
  }

  @DeleteMapping("/{id}")
  @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
  @AuthActionScope("boq.delete")
  @Operation(summary = "Delete BoQ", description = "Delete a bill of quantities")
  public ResponseEntity<Void> deleteBoQ(@PathVariable UUID id) {
    boqService.deleteBoQ(id);
    return ResponseEntity.noContent().build();
  }
}
