package com.housingplatform.construction.api;

import com.housingplatform.construction.dto.MaterialResponse;
import com.housingplatform.construction.service.MaterialService;
import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.dto.OrganizationResponse;
import com.housingplatform.identity.service.OrganizationService;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/suppliers")
@Tag(name = "Suppliers", description = "Material supplier management APIs")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
public class SupplierController {
    
    private final OrganizationService organizationService;
    private final MaterialService materialService;
    
    @GetMapping
    @Operation(summary = "List suppliers", description = "Retrieve all approved supplier organizations")
    public ResponseEntity<List<OrganizationResponse>> getAllSuppliers(
            @RequestParam(required = false) String status) {
        String filterStatus = status != null ? status : "APPROVED";
        List<OrganizationResponse> suppliers = organizationService.getAllOrganizations("SUPPLIER", filterStatus, null);
        return ResponseEntity.ok(suppliers);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get supplier by ID", description = "Retrieve supplier organization information by ID")
    public ResponseEntity<OrganizationResponse> getSupplierById(@PathVariable UUID id) {
        OrganizationResponse supplier = organizationService.getOrganizationById(id);
        
        // Verify it's a supplier
        if (supplier.getType() != Organization.OrganizationType.SUPPLIER) {
            throw new IllegalArgumentException("Organization is not a supplier");
        }
        
        return ResponseEntity.ok(supplier);
    }
    
    @GetMapping("/my-supplier")
    @AuthPolicyScope(AuthPolicyScope.Policy.SUPPLIER_SECURED)
    @Operation(summary = "Get my supplier organization", description = "Get the current user's supplier organization")
    public ResponseEntity<OrganizationResponse> getMySupplier() {
        OrganizationResponse supplier = organizationService.getMySupplier();
        return ResponseEntity.ok(supplier);
    }
    
    @GetMapping("/{supplierId}/materials")
    @Operation(summary = "Get supplier materials", description = "Retrieve all materials offered by a supplier")
    public ResponseEntity<Page<MaterialResponse>> getSupplierMaterials(
            @PathVariable UUID supplierId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<MaterialResponse> materials = materialService.getAllMaterials(supplierId, category, pageable);
        return ResponseEntity.ok(materials);
    }
    
    @GetMapping("/{supplierId}/materials/categories")
    @Operation(summary = "Get supplier material categories", description = "Retrieve all unique material categories offered by a supplier")
    public ResponseEntity<List<String>> getSupplierMaterialCategories(@PathVariable UUID supplierId) {
        // This would need to be implemented in MaterialService
        // For now, return empty list
        return ResponseEntity.ok(List.of());
    }
    
    @GetMapping("/search")
    @Operation(summary = "Search suppliers", description = "Search suppliers by name, city, or other criteria")
    public ResponseEntity<List<OrganizationResponse>> searchSuppliers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String country) {
        List<OrganizationResponse> allSuppliers = organizationService.getAllOrganizations("SUPPLIER", "APPROVED", null);
        
        // Filter by criteria
        List<OrganizationResponse> filtered = allSuppliers.stream()
                .filter(supplier -> {
                    boolean matches = true;
                    if (name != null && !name.isEmpty()) {
                        matches = matches && supplier.getName().toLowerCase().contains(name.toLowerCase());
                    }
                    if (city != null && !city.isEmpty()) {
                        matches = matches && supplier.getCity() != null && 
                                supplier.getCity().toLowerCase().contains(city.toLowerCase());
                    }
                    if (country != null && !country.isEmpty()) {
                        matches = matches && supplier.getCountry() != null && 
                                supplier.getCountry().toLowerCase().contains(country.toLowerCase());
                    }
                    return matches;
                })
                .toList();
        
        return ResponseEntity.ok(filtered);
    }
}
