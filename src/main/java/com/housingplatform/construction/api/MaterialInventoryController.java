package com.housingplatform.construction.api;

import com.housingplatform.construction.dto.MaterialInventoryRequest;
import com.housingplatform.construction.dto.MaterialInventoryResponse;
import com.housingplatform.construction.domain.MaterialInventory;
import com.housingplatform.construction.service.MaterialInventoryService;
import com.housingplatform.shared.security.annotation.AuthActionScope;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
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
@RequestMapping("/api/v1/material-inventory")
@Tag(name = "Material Inventory", description = "Material inventory management APIs")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
public class MaterialInventoryController {
    
    private final MaterialInventoryService inventoryService;
    
    @PostMapping
    @AuthActionScope("construction.inventory.create")
    @Operation(summary = "Create inventory entry", description = "Create a new material inventory entry")
    public ResponseEntity<MaterialInventoryResponse> createInventory(@Valid @RequestBody MaterialInventoryRequest request) {
        MaterialInventoryResponse created = inventoryService.createInventory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get inventory by ID", description = "Retrieve material inventory information by ID")
    public ResponseEntity<MaterialInventoryResponse> getInventoryById(@PathVariable UUID id) {
        MaterialInventoryResponse inventory = inventoryService.getInventoryById(id);
        return ResponseEntity.ok(inventory);
    }
    
    @GetMapping("/material/{materialId}")
    @Operation(summary = "Get inventory by material", description = "Retrieve all inventory entries for a specific material")
    public ResponseEntity<List<MaterialInventoryResponse>> getInventoryByMaterial(@PathVariable UUID materialId) {
        List<MaterialInventoryResponse> inventory = inventoryService.getInventoryByMaterial(materialId);
        return ResponseEntity.ok(inventory);
    }
    
    @GetMapping("/project/{projectId}")
    @Operation(summary = "Get inventory by project", description = "Retrieve all inventory entries for a specific project")
    public ResponseEntity<List<MaterialInventoryResponse>> getInventoryByProject(@PathVariable UUID projectId) {
        List<MaterialInventoryResponse> inventory = inventoryService.getInventoryByProject(projectId);
        return ResponseEntity.ok(inventory);
    }
    
    @GetMapping("/material/{materialId}/project/{projectId}")
    @Operation(summary = "Get inventory by material and project", description = "Retrieve inventory entry for a specific material and project")
    public ResponseEntity<MaterialInventoryResponse> getInventoryByMaterialAndProject(
            @PathVariable UUID materialId,
            @PathVariable UUID projectId) {
        MaterialInventoryResponse inventory = inventoryService.getInventoryByMaterialAndProject(materialId, projectId);
        return ResponseEntity.ok(inventory);
    }
    
    @GetMapping("/low-stock")
    @Operation(summary = "Get low stock items", description = "Retrieve all inventory items that are below minimum stock level")
    public ResponseEntity<List<MaterialInventoryResponse>> getLowStockItems() {
        List<MaterialInventoryResponse> items = inventoryService.getLowStockItems();
        return ResponseEntity.ok(items);
    }
    
    @GetMapping("/status/{status}")
    @Operation(summary = "Get inventory by status", description = "Retrieve inventory entries filtered by status")
    public ResponseEntity<Page<MaterialInventoryResponse>> getInventoryByStatus(
            @PathVariable MaterialInventory.InventoryStatus status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<MaterialInventoryResponse> inventory = inventoryService.getInventoryByStatus(status, pageable);
        return ResponseEntity.ok(inventory);
    }
    
    @PutMapping("/{id}")
    @AuthActionScope("construction.inventory.update")
    @Operation(summary = "Update inventory", description = "Update material inventory information")
    public ResponseEntity<MaterialInventoryResponse> updateInventory(
            @PathVariable UUID id,
            @Valid @RequestBody MaterialInventoryRequest request) {
        MaterialInventoryResponse updated = inventoryService.updateInventory(id, request);
        return ResponseEntity.ok(updated);
    }
    
    @PutMapping("/{id}/adjust")
    @AuthActionScope("construction.inventory.update")
    @Operation(summary = "Adjust inventory", description = "Adjust inventory quantity (increase or decrease)")
    public ResponseEntity<MaterialInventoryResponse> adjustInventory(
            @PathVariable UUID id,
            @RequestParam java.math.BigDecimal quantityChange,
            @RequestParam(required = false) String reason) {
        MaterialInventoryResponse updated = inventoryService.adjustInventory(id, quantityChange, reason);
        return ResponseEntity.ok(updated);
    }
    
    @PutMapping("/{id}/reserve")
    @AuthActionScope("construction.inventory.update")
    @Operation(summary = "Reserve inventory", description = "Reserve inventory quantity for future use")
    public ResponseEntity<MaterialInventoryResponse> reserveInventory(
            @PathVariable UUID id,
            @RequestParam java.math.BigDecimal quantity) {
        MaterialInventoryResponse updated = inventoryService.reserveInventory(id, quantity);
        return ResponseEntity.ok(updated);
    }
    
    @PutMapping("/{id}/release")
    @AuthActionScope("construction.inventory.update")
    @Operation(summary = "Release reservation", description = "Release reserved inventory quantity")
    public ResponseEntity<MaterialInventoryResponse> releaseReservation(
            @PathVariable UUID id,
            @RequestParam java.math.BigDecimal quantity) {
        MaterialInventoryResponse updated = inventoryService.releaseReservation(id, quantity);
        return ResponseEntity.ok(updated);
    }
    
    @DeleteMapping("/{id}")
    @AuthActionScope("construction.inventory.delete")
    @Operation(summary = "Delete inventory", description = "Delete a material inventory entry (only if quantity is zero)")
    public ResponseEntity<Void> deleteInventory(@PathVariable UUID id) {
        inventoryService.deleteInventory(id);
        return ResponseEntity.noContent().build();
    }
}
