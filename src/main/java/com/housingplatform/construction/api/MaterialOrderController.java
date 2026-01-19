package com.housingplatform.construction.api;

import com.housingplatform.construction.dto.MaterialOrderRequest;
import com.housingplatform.construction.dto.MaterialOrderResponse;
import com.housingplatform.construction.domain.MaterialOrder;
import com.housingplatform.construction.service.MaterialOrderService;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/material-orders")
@Tag(name = "Material Orders", description = "Material order management APIs")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
public class MaterialOrderController {
    
    private final MaterialOrderService orderService;
    
    @PostMapping
    @AuthActionScope("construction.orders.create")
    @Operation(summary = "Create material order", description = "Create a new material order")
    public ResponseEntity<MaterialOrderResponse> createOrder(@Valid @RequestBody MaterialOrderRequest request) {
        UUID userId = UserContext.getCurrentUserId();
        MaterialOrderResponse created = orderService.createOrder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID", description = "Retrieve material order information by ID")
    public ResponseEntity<MaterialOrderResponse> getOrderById(@PathVariable UUID id) {
        MaterialOrderResponse order = orderService.getOrderById(id);
        return ResponseEntity.ok(order);
    }
    
    @GetMapping("/order-number/{orderNumber}")
    @Operation(summary = "Get order by order number", description = "Retrieve material order by order number")
    public ResponseEntity<MaterialOrderResponse> getOrderByOrderNumber(@PathVariable String orderNumber) {
        MaterialOrderResponse order = orderService.getOrderByOrderNumber(orderNumber);
        return ResponseEntity.ok(order);
    }
    
    @GetMapping
    @Operation(summary = "List orders", description = "Retrieve material orders with optional filtering")
    public ResponseEntity<Page<MaterialOrderResponse>> getOrders(
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) MaterialOrder.OrderStatus status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<MaterialOrderResponse> orders;
        
        if (supplierId != null) {
            orders = orderService.getOrdersBySupplier(supplierId, status, pageable);
        } else if (projectId != null) {
            orders = orderService.getOrdersByProject(projectId, pageable);
        } else {
            // Get orders for current user's organization
            UUID companyId = UserContext.getCurrentUserOrganizationId()
                    .orElseThrow(() -> new IllegalStateException("User must be associated with an organization"));
            orders = orderService.getOrdersBySupplier(companyId, status, pageable);
        }
        
        return ResponseEntity.ok(orders);
    }
    
    @PutMapping("/{id}/status")
    @AuthActionScope("construction.orders.update")
    @Operation(summary = "Update order status", description = "Update the status of a material order")
    public ResponseEntity<MaterialOrderResponse> updateOrderStatus(
            @PathVariable UUID id,
            @RequestParam MaterialOrder.OrderStatus status) {
        MaterialOrderResponse updated = orderService.updateOrderStatus(id, status);
        return ResponseEntity.ok(updated);
    }
    
    @PutMapping("/{id}/delivery")
    @AuthActionScope("construction.orders.update")
    @Operation(summary = "Update order delivery", description = "Record delivery date for a material order")
    public ResponseEntity<MaterialOrderResponse> updateOrderDelivery(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate) {
        MaterialOrderResponse updated = orderService.updateOrderDelivery(id, deliveryDate);
        return ResponseEntity.ok(updated);
    }
    
    @PutMapping("/{orderId}/items/{itemId}/receive")
    @AuthActionScope("construction.orders.update")
    @Operation(summary = "Receive order items", description = "Record received quantity for order items")
    public ResponseEntity<MaterialOrderResponse> receiveOrderItems(
            @PathVariable UUID orderId,
            @PathVariable UUID itemId,
            @RequestParam java.math.BigDecimal receivedQuantity) {
        MaterialOrderResponse updated = orderService.receiveOrderItems(orderId, itemId, receivedQuantity);
        return ResponseEntity.ok(updated);
    }
    
    @DeleteMapping("/{id}")
    @AuthActionScope("construction.orders.delete")
    @Operation(summary = "Delete order", description = "Delete a material order (only draft orders can be deleted)")
    public ResponseEntity<Void> deleteOrder(@PathVariable UUID id) {
        UUID userId = UserContext.getCurrentUserId();
        orderService.deleteOrder(userId, id);
        return ResponseEntity.noContent().build();
    }
}
