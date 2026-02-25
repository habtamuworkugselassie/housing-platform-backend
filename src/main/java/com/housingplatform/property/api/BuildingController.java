package com.housingplatform.property.api;

import com.housingplatform.banking.dto.FinancingOfferResponse;
import com.housingplatform.banking.service.FinancingOfferService;
import com.housingplatform.property.dto.BuildingRequest;
import com.housingplatform.property.dto.BuildingResponse;
import com.housingplatform.property.service.BuildingService;
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
@RequestMapping("/api/v1/buildings")
@Tag(name = "Buildings", description = "Building management APIs")
@RequiredArgsConstructor
public class BuildingController {

  private final BuildingService buildingService;
  private final FinancingOfferService financingOfferService;

  @GetMapping
  @AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
  @Operation(
      summary = "List buildings",
      description = "Retrieve all buildings with optional filtering. Public access.")
  public ResponseEntity<List<BuildingResponse>> getAllBuildings(
      @RequestParam(required = false) String city,
      @RequestParam(required = false) String buildingType) {
    List<BuildingResponse> buildings = buildingService.getAllBuildings(city, buildingType);
    return ResponseEntity.ok(buildings);
  }

  @GetMapping("/{id}")
  @AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
  @Operation(
      summary = "Get building",
      description = "Retrieve building information by ID. Public access.")
  public ResponseEntity<BuildingResponse> getBuildingById(@PathVariable UUID id) {
    BuildingResponse building = buildingService.getBuildingById(id);
    return ResponseEntity.ok(building);
  }

  @GetMapping("/companies/{companyId}")
  @Operation(
      summary = "Get company buildings",
      description = "Retrieve all buildings for a real estate company")
  public ResponseEntity<List<BuildingResponse>> getBuildingsByCompany(
      @PathVariable UUID companyId) {
    List<BuildingResponse> buildings = buildingService.getBuildingsByCompanyId(companyId);
    return ResponseEntity.ok(buildings);
  }

  @PostMapping("/companies/{companyId}")
  @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
  @AuthActionScope("buildings.create")
  @Operation(summary = "Create building", description = "Create a new building")
  public ResponseEntity<BuildingResponse> createBuilding(
      @PathVariable UUID companyId, @Valid @RequestBody BuildingRequest buildingRequest) {
    BuildingResponse created = buildingService.createBuilding(companyId, buildingRequest);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PutMapping("/{buildingId}/companies/{companyId}")
  @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
  @AuthActionScope("buildings.update")
  @Operation(summary = "Update building", description = "Update building information")
  public ResponseEntity<BuildingResponse> updateBuilding(
      @PathVariable UUID companyId,
      @PathVariable UUID buildingId,
      @Valid @RequestBody BuildingRequest buildingRequest) {
    BuildingResponse updated =
        buildingService.updateBuilding(companyId, buildingId, buildingRequest);
    return ResponseEntity.ok(updated);
  }

  @DeleteMapping("/{buildingId}/companies/{companyId}")
  @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
  @AuthActionScope("buildings.delete")
  @Operation(
      summary = "Delete building",
      description = "Delete a building (only if it has no units)")
  public ResponseEntity<Void> deleteBuilding(
      @PathVariable UUID companyId, @PathVariable UUID buildingId) {
    buildingService.deleteBuilding(companyId, buildingId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/units")
  @AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
  @Operation(
      summary = "Get building units",
      description = "Retrieve all properties/units in a building. Public access.")
  public ResponseEntity<List<com.housingplatform.property.dto.PropertyResponse>> getBuildingUnits(
      @PathVariable UUID id) {
    BuildingResponse building = buildingService.getBuildingById(id);
    return ResponseEntity.ok(building.getUnits() != null ? building.getUnits() : List.of());
  }

  @GetMapping("/{id}/financing-offers")
  @AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
  @Operation(
      summary = "Get financing offers for building",
      description =
          "Retrieve all financing offers available for a specific building. Public access.")
  public ResponseEntity<List<FinancingOfferResponse>> getFinancingOffers(@PathVariable UUID id) {
    List<FinancingOfferResponse> offers = financingOfferService.getFinancingOffersByBuildingId(id);
    return ResponseEntity.ok(offers);
  }

  @GetMapping("/search")
  @AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
  @Operation(
      summary = "Search buildings",
      description = "Search buildings by company name, location, or other criteria. Public access.")
  public ResponseEntity<List<BuildingResponse>> searchBuildings(
      @RequestParam(required = false) String companyName,
      @RequestParam(required = false) String city,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String country,
      @RequestParam(required = false) String name,
      @RequestParam(defaultValue = "50") Integer limit) {
    List<BuildingResponse> buildings =
        buildingService.searchBuildings(companyName, city, state, country, name, limit);
    return ResponseEntity.ok(buildings);
  }
}
