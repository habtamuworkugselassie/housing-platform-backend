package com.housingplatform.construction.api;

import com.housingplatform.construction.dto.MaterialRequest;
import com.housingplatform.construction.dto.MaterialResponse;
import com.housingplatform.construction.service.MaterialService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/materials")
@Tag(name = "Materials", description = "Construction material management APIs")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
public class MaterialController {
    
    private final MaterialService materialService;
    
    @GetMapping
    @Operation(summary = "List materials", description = "Retrieve all materials with optional filtering")
    public ResponseEntity<Page<MaterialResponse>> getAllMaterials(
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<MaterialResponse> materials = materialService.getAllMaterials(supplierId, category, pageable);
        return ResponseEntity.ok(materials);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get material by ID", description = "Retrieve material information by ID")
    public ResponseEntity<MaterialResponse> getMaterialById(@PathVariable UUID id) {
        MaterialResponse material = materialService.getMaterialById(id);
        return ResponseEntity.ok(material);
    }
    
    @PostMapping
    @AuthPolicyScope(AuthPolicyScope.Policy.SUPPLIER_SECURED)
    @AuthActionScope("materials.create")
    @Operation(summary = "Create material", description = "Create a new material listing (supplier only)")
    public ResponseEntity<MaterialResponse> createMaterial(@Valid @RequestBody MaterialRequest materialRequest) {
        UUID supplierId = UserContext.getCurrentUserId();
        MaterialResponse created = materialService.createMaterial(supplierId, materialRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @PutMapping("/{id}")
    @AuthPolicyScope(AuthPolicyScope.Policy.SUPPLIER_SECURED)
    @AuthActionScope("materials.update")
    @Operation(summary = "Update material", description = "Update material information (supplier only)")
    public ResponseEntity<MaterialResponse> updateMaterial(
            @PathVariable UUID id,
            @Valid @RequestBody MaterialRequest materialRequest) {
        UUID supplierId = UserContext.getCurrentUserId();
        MaterialResponse updated = materialService.updateMaterial(supplierId, id, materialRequest);
        return ResponseEntity.ok(updated);
    }
    
    @DeleteMapping("/{id}")
    @AuthPolicyScope(AuthPolicyScope.Policy.SUPPLIER_SECURED)
    @AuthActionScope("materials.delete")
    @Operation(summary = "Delete material", description = "Delete a material (supplier only)")
    public ResponseEntity<Void> deleteMaterial(@PathVariable UUID id) {
        UUID supplierId = UserContext.getCurrentUserId();
        materialService.deleteMaterial(supplierId, id);
        return ResponseEntity.noContent().build();
    }
}
