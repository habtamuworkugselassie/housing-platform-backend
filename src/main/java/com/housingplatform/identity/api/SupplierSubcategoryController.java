package com.housingplatform.identity.api;

import com.housingplatform.identity.dto.SupplierSubcategoryRequest;
import com.housingplatform.identity.dto.SupplierSubcategoryResponse;
import com.housingplatform.identity.service.SupplierSubcategoryService;
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
@RequestMapping("/api/v1/supplier-subcategories")
@Tag(
    name = "Supplier subcategories",
    description = "Construction material supplier marketplace subcategories (cement, steel, etc.)")
@RequiredArgsConstructor
public class SupplierSubcategoryController {

  private final SupplierSubcategoryService supplierSubcategoryService;

  @GetMapping
  @AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
  @Operation(summary = "List active supplier subcategories", description = "Public. For marketplace filters.")
  public ResponseEntity<List<SupplierSubcategoryResponse>> listActive() {
    return ResponseEntity.ok(supplierSubcategoryService.listActive());
  }

  @GetMapping("/admin")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @Operation(summary = "List all supplier subcategories", description = "Admin. Includes inactive.")
  public ResponseEntity<List<SupplierSubcategoryResponse>> listAllForAdmin() {
    return ResponseEntity.ok(supplierSubcategoryService.listAllForAdmin());
  }

  @PostMapping
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @Operation(summary = "Create supplier subcategory", description = "Admin only.")
  public ResponseEntity<SupplierSubcategoryResponse> create(
      @Valid @RequestBody SupplierSubcategoryRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(supplierSubcategoryService.create(request));
  }

  @PutMapping("/{id}")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @Operation(summary = "Update supplier subcategory", description = "Admin only.")
  public ResponseEntity<SupplierSubcategoryResponse> update(
      @PathVariable UUID id, @Valid @RequestBody SupplierSubcategoryRequest request) {
    return ResponseEntity.ok(supplierSubcategoryService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @Operation(summary = "Delete supplier subcategory", description = "Admin only; fails if still assigned.")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    supplierSubcategoryService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
