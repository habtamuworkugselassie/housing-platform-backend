package com.housingplatform.purchase.api;

import com.housingplatform.purchase.domain.PropertyPurchaseOrder.PurchaseOrderStatus;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder.PurchaseType;
import com.housingplatform.purchase.dto.AdminPurchaseOrderFilter;
import com.housingplatform.purchase.dto.PurchaseOrderResponse;
import com.housingplatform.purchase.dto.PurchaseOrderStatsResponse;
import com.housingplatform.purchase.service.PurchaseOrderService;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-wide view of purchase orders for administrators. A single order is read through the
 * regular {@code GET /api/v1/purchase-orders/{id}}, which already admits admins.
 */
@RestController
@RequestMapping("/api/v1/admin/purchase-orders")
@Tag(name = "Admin - Purchase Orders")
@AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
@RequiredArgsConstructor
public class AdminPurchaseOrderController {

  private static final int MAX_PAGE_SIZE = 200;

  private final PurchaseOrderService purchaseOrderService;

  @GetMapping
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @Operation(
      summary = "List every purchase order",
      description =
          "Newest first. Filters are optional and combine with AND; `q` matches the order number,"
              + " contact phone or contact email. Dates are inclusive from / exclusive to, in the"
              + " server's local date.")
  public ResponseEntity<Page<PurchaseOrderResponse>> list(
      @RequestParam(required = false) PurchaseOrderStatus status,
      @RequestParam(required = false) PurchaseType purchaseType,
      @RequestParam(required = false) UUID realEstateCompanyId,
      @RequestParam(required = false) UUID bankId,
      @RequestParam(required = false) UUID buyerId,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate createdFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate createdTo,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    AdminPurchaseOrderFilter filter =
        new AdminPurchaseOrderFilter(
            status,
            purchaseType,
            realEstateCompanyId,
            bankId,
            buyerId,
            q,
            createdFrom == null ? null : createdFrom.atStartOfDay(),
            createdTo == null ? null : createdTo.plusDays(1).atStartOfDay());
    Pageable pageable =
        PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
            Sort.by(Sort.Direction.DESC, "createdAt"));
    return ResponseEntity.ok(purchaseOrderService.searchAll(filter, pageable));
  }

  @GetMapping("/stats")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @Operation(summary = "Order counts by status for the admin overview")
  public ResponseEntity<PurchaseOrderStatsResponse> stats() {
    return ResponseEntity.ok(purchaseOrderService.adminStats());
  }
}
