package com.housingplatform.property.api;

import com.housingplatform.banking.dto.FinancingOfferResponse;
import com.housingplatform.banking.service.FinancingOfferService;
import com.housingplatform.identity.service.RealEstateAgentService;
import com.housingplatform.property.dto.PropertyCreditCoverageRequest;
import com.housingplatform.property.dto.PropertyRequest;
import com.housingplatform.property.dto.PropertyResponse;
import com.housingplatform.property.service.PropertyService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/properties")
@Tag(name = "Properties", description = "Property management APIs")
@RequiredArgsConstructor
public class PropertyController {

  private final PropertyService propertyService;
  private final FinancingOfferService financingOfferService;
  private final RealEstateAgentService agentService;

  @GetMapping
  @AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
  @Operation(
      summary = "List all properties",
      description =
          "Retrieve a list of all properties. Public access shows only AVAILABLE properties.")
  public ResponseEntity<Page<PropertyResponse>> getAllProperties(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String city,
      @RequestParam(defaultValue = "0") Integer page,
      @RequestParam(defaultValue = "20") Integer size) {
    Pageable pageable = PageRequest.of(page, size);
    boolean isAuthenticated = isAuthenticated();
    boolean publicOnly = !isAuthenticated;
    Page<PropertyResponse> properties =
        propertyService.getAllProperties(status, city, pageable, publicOnly);
    return ResponseEntity.ok(properties);
  }

  @GetMapping("/{id}")
  @AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
  @Operation(
      summary = "Get property by ID",
      description = "Retrieve detailed information about a specific property. Public access.")
  public ResponseEntity<PropertyResponse> getPropertyById(@PathVariable UUID id) {
    PropertyResponse property = propertyService.getPropertyById(id);
    return ResponseEntity.ok(property);
  }

  @PostMapping
  @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
  @AuthActionScope("properties.create")
  @Operation(summary = "Create new property", description = "Create a new property listing")
  public ResponseEntity<PropertyResponse> createProperty(
      @Valid @RequestBody PropertyRequest propertyRequest) {
    UUID userId = UserContext.getCurrentUserId();
    UUID agentId = getAgentIdForUser(userId);
    PropertyResponse created = propertyService.createProperty(propertyRequest, agentId);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PutMapping("/{id}")
  @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
  @AuthActionScope("properties.update")
  @Operation(summary = "Update property", description = "Update an existing property listing")
  public ResponseEntity<PropertyResponse> updateProperty(
      @PathVariable UUID id, @Valid @RequestBody PropertyRequest propertyRequest) {
    UUID userId = UserContext.getCurrentUserId();
    UUID agentId = getAgentIdForUser(userId);
    PropertyResponse updated = propertyService.updateProperty(id, propertyRequest, agentId);
    return ResponseEntity.ok(updated);
  }

  @DeleteMapping("/{id}")
  @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
  @AuthActionScope("properties.delete")
  @Operation(summary = "Delete property", description = "Delete a property listing")
  public ResponseEntity<Void> deleteProperty(@PathVariable UUID id) {
    UUID userId = UserContext.getCurrentUserId();
    UUID agentId = getAgentIdForUser(userId);
    propertyService.deleteProperty(id, agentId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/agent/{agentId}")
  @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
  @Operation(
      summary = "Get properties by agent",
      description = "Get all properties for a specific agent")
  public ResponseEntity<List<PropertyResponse>> getPropertiesByAgent(@PathVariable UUID agentId) {
    List<PropertyResponse> properties = propertyService.getPropertiesByAgentId(agentId);
    return ResponseEntity.ok(properties);
  }

  @GetMapping("/organization/{organizationId}")
  @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
  @Operation(
      summary = "Get properties by organization",
      description = "Get all properties for a specific organization (super agents only)")
  public ResponseEntity<List<PropertyResponse>> getPropertiesByOrganization(
      @PathVariable UUID organizationId) {
    List<PropertyResponse> properties = propertyService.getPropertiesByCompanyId(organizationId);
    return ResponseEntity.ok(properties);
  }

  @GetMapping("/organization/{organizationId}/list")
  @AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
  @Operation(
      summary = "List available properties by organization (marketplace)",
      description =
          "Public. Returns AVAILABLE properties for the given real estate company, with sponsorship"
              + " enrichment. Used for home/marketplace organization-grouped listing.")
  public ResponseEntity<List<PropertyResponse>> getAvailablePropertiesByOrganization(
      @PathVariable UUID organizationId) {
    List<PropertyResponse> properties =
        propertyService.getAvailablePropertiesByCompanyIdForMarketplace(organizationId);
    return ResponseEntity.ok(properties);
  }

  @GetMapping("/organization/{organizationId}/first-media")
  @AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
  @Operation(
      summary = "Get first property media for organization",
      description =
          "First image and video from any of the organization's properties. Public, for sponsor"
              + " carousel fallback when the organization has no logo/video.")
  public ResponseEntity<com.housingplatform.property.dto.FirstPropertyMediaResponse>
      getFirstPropertyMediaForOrganization(@PathVariable UUID organizationId) {
    com.housingplatform.property.dto.FirstPropertyMediaResponse response =
        propertyService.getFirstPropertyMediaForOrganization(organizationId);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{id}/financing-offers")
  @AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
  @Operation(
      summary = "Get financing offers for property",
      description = "Retrieve all financing offers available for a specific property")
  public ResponseEntity<List<FinancingOfferResponse>> getFinancingOffers(@PathVariable UUID id) {
    List<FinancingOfferResponse> offers = financingOfferService.getFinancingOffersByPropertyId(id);
    return ResponseEntity.ok(offers);
  }

  @PostMapping("/{id}/financing-offers/link")
  @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
  @AuthActionScope("properties.update")
  @Operation(
      summary = "Link credit product to property",
      description =
          "Create an active financing offer by linking a bank credit product to the selected"
              + " property")
  public ResponseEntity<FinancingOfferResponse> linkCreditProductToProperty(
      @PathVariable UUID id, @RequestParam UUID bankId, @RequestParam UUID creditProductId) {
    FinancingOfferResponse created =
        financingOfferService.linkCreditProductToProperty(bankId, creditProductId, id);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PostMapping("/{id}/financing-offers")
  @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
  @AuthActionScope("properties.update")
  @Operation(
      summary = "Create property financing offer",
      description =
          "Create a simplified financing offer for a property using selected bank and coverage"
              + " percentage. The system automatically chooses an active credit product for that"
              + " bank.")
  public ResponseEntity<FinancingOfferResponse> createPropertyFinancingOffer(
      @PathVariable UUID id,
      @RequestParam UUID bankId,
      @Valid @RequestBody PropertyCreditCoverageRequest request) {
    FinancingOfferResponse created =
        financingOfferService.createPropertyCreditOffer(
            bankId, id, request.getCoveragePercentage());
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping("/search")
  @AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
  @Operation(
      summary = "Search properties",
      description =
          "Search properties by company name, location, or other criteria. Public access.")
  public ResponseEntity<List<PropertyResponse>> searchProperties(
      @RequestParam(required = false) String companyName,
      @RequestParam(required = false) String city,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String country,
      @RequestParam(required = false) String title,
      @RequestParam(defaultValue = "50") Integer limit) {
    List<PropertyResponse> properties =
        propertyService.searchProperties(companyName, city, state, country, title, limit);
    return ResponseEntity.ok(properties);
  }

  @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
  @AuthActionScope("properties.update")
  @Operation(
      summary = "Upload property images/videos",
      description = "Upload photos or videos for a property")
  public ResponseEntity<PropertyResponse> uploadPropertyMedia(
      @PathVariable UUID id,
      @RequestParam("files") List<MultipartFile> files,
      @RequestParam(required = false) List<String> captions) {
    UUID userId = UserContext.getCurrentUserId();
    UUID agentId = getAgentIdForUser(userId);
    PropertyResponse updated = propertyService.uploadPropertyMedia(id, files, captions, agentId);
    return ResponseEntity.ok(updated);
  }

  @DeleteMapping("/{id}/images/{imageId}")
  @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
  @AuthActionScope("properties.update")
  @Operation(
      summary = "Delete property image",
      description = "Delete a specific image from a property")
  public ResponseEntity<PropertyResponse> deletePropertyImage(
      @PathVariable UUID id, @PathVariable UUID imageId) {
    UUID userId = UserContext.getCurrentUserId();
    UUID agentId = getAgentIdForUser(userId);
    PropertyResponse updated = propertyService.deletePropertyImage(id, imageId, agentId);
    return ResponseEntity.ok(updated);
  }

  @GetMapping("/{id}/images/{imageId}/file")
  @AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
  @Operation(
      summary = "Get property image/video file",
      description = "Retrieve the actual image or video file from database")
  public ResponseEntity<byte[]> getPropertyImageFile(
      @PathVariable UUID id, @PathVariable UUID imageId) {
    return propertyService.getPropertyImageFile(id, imageId);
  }

  private boolean isAuthenticated() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
        && authentication.isAuthenticated()
        && !"anonymousUser".equals(authentication.getPrincipal());
  }

  private UUID getAgentIdForUser(UUID userId) {
    try {
      return agentService.getAgentByUserId(userId).getId();
    } catch (Exception e) {
      return null; // User is not registered as an agent
    }
  }
}
