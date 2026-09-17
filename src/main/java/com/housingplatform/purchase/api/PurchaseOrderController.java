package com.housingplatform.purchase.api;

import com.housingplatform.purchase.domain.PropertyPurchaseOrder.PurchaseOrderStatus;
import com.housingplatform.purchase.dto.CreatePurchaseOrderRequest;
import com.housingplatform.purchase.dto.PurchaseOrderDecisionRequest;
import com.housingplatform.purchase.dto.PurchaseOrderResponse;
import com.housingplatform.purchase.dto.RejectPurchaseOrderRequest;
import com.housingplatform.purchase.dto.UpdatePurchaseFinancingRequest;
import com.housingplatform.purchase.service.PurchaseOrderService;
import com.housingplatform.shared.security.annotation.AuthActionScope;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/purchase-orders")
@Tag(
    name = "Purchase Orders",
    description = "Property purchase orders with optional bank financing")
@RequiredArgsConstructor
public class PurchaseOrderController {

  private final PurchaseOrderService purchaseOrderService;
  private final PurchaseOrderActorResolver actors;

  @PostMapping
  @AuthPolicyScope(AuthPolicyScope.Policy.BUYER_SECURED)
  @AuthActionScope("purchase-orders.create")
  @Operation(
      summary = "Create a purchase order",
      description =
          "Places an order on a property. Phone is mandatory, email optional. When the property has"
              + " an active financing product the order is bank financed and a loan application is"
              + " opened; otherwise it is a cash order. Partial financing is expressed through"
              + " financing.financedAmount or financing.downPaymentAmount.")
  public ResponseEntity<PurchaseOrderResponse> create(
      @Valid @RequestBody CreatePurchaseOrderRequest request) {
    PurchaseOrderResponse created =
        purchaseOrderService.createPurchaseOrder(actors.current(), request);
    return ResponseEntity.created(URI.create("/api/v1/purchase-orders/" + created.getId()))
        .body(created);
  }

  @GetMapping("/{id}")
  @AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
  @Operation(
      summary = "Get a purchase order",
      description = "Visible to the buyer, the seller, the financing bank and admins")
  public ResponseEntity<PurchaseOrderResponse> get(@PathVariable UUID id) {
    return ResponseEntity.ok(purchaseOrderService.getPurchaseOrder(actors.current(), id));
  }

  @GetMapping("/me")
  @AuthPolicyScope(AuthPolicyScope.Policy.BUYER_SECURED)
  @Operation(summary = "List my purchase orders")
  public ResponseEntity<Page<PurchaseOrderResponse>> mine(
      @RequestParam(required = false) PurchaseOrderStatus status,
      @RequestParam(defaultValue = "0") Integer page,
      @RequestParam(defaultValue = "20") Integer size) {
    return ResponseEntity.ok(
        purchaseOrderService.getMyPurchaseOrders(
            actors.current(), status, PageRequest.of(page, size)));
  }

  @GetMapping("/received")
  @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
  @Operation(summary = "List purchase orders received on my company's listings")
  public ResponseEntity<Page<PurchaseOrderResponse>> received(
      @RequestParam(required = false) PurchaseOrderStatus status,
      @RequestParam(defaultValue = "0") Integer page,
      @RequestParam(defaultValue = "20") Integer size) {
    return ResponseEntity.ok(
        purchaseOrderService.getReceivedPurchaseOrders(
            actors.current(), status, PageRequest.of(page, size)));
  }

  @GetMapping("/financed")
  @AuthPolicyScope(AuthPolicyScope.Policy.BANKER_SECURED)
  @Operation(summary = "List purchase orders financed by my bank")
  public ResponseEntity<Page<PurchaseOrderResponse>> financed(
      @RequestParam(required = false) PurchaseOrderStatus status,
      @RequestParam(defaultValue = "0") Integer page,
      @RequestParam(defaultValue = "20") Integer size) {
    return ResponseEntity.ok(
        purchaseOrderService.getFinancedPurchaseOrders(
            actors.current(), status, PageRequest.of(page, size)));
  }

  @PutMapping("/{id}/financing")
  @AuthPolicyScope(AuthPolicyScope.Policy.BUYER_SECURED)
  @Operation(
      summary = "Change the financing split",
      description =
          "Before seller acceptance: updates the submitted loan application. After a financing"
              + " rejection: re-applies for a smaller amount.")
  public ResponseEntity<PurchaseOrderResponse> updateFinancing(
      @PathVariable UUID id, @Valid @RequestBody UpdatePurchaseFinancingRequest request) {
    return ResponseEntity.ok(purchaseOrderService.updateFinancing(actors.current(), id, request));
  }

  @PostMapping("/{id}/accept")
  @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
  @AuthActionScope("purchase-orders.review")
  @Operation(summary = "Seller accepts the order", description = "Reserves the property")
  public ResponseEntity<PurchaseOrderResponse> accept(
      @PathVariable UUID id,
      @Valid @RequestBody(required = false) PurchaseOrderDecisionRequest request) {
    return ResponseEntity.ok(
        purchaseOrderService.accept(
            actors.current(), id, request != null ? request.getNotes() : null));
  }

  @PostMapping("/{id}/reject")
  @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
  @AuthActionScope("purchase-orders.review")
  @Operation(summary = "Seller rejects the order")
  public ResponseEntity<PurchaseOrderResponse> reject(
      @PathVariable UUID id, @Valid @RequestBody RejectPurchaseOrderRequest request) {
    return ResponseEntity.ok(
        purchaseOrderService.reject(actors.current(), id, request.getReason()));
  }

  @PostMapping("/{id}/cancel")
  @AuthPolicyScope(AuthPolicyScope.Policy.BUYER_SECURED)
  @AuthActionScope("purchase-orders.cancel")
  @Operation(summary = "Buyer cancels the order")
  public ResponseEntity<PurchaseOrderResponse> cancel(
      @PathVariable UUID id,
      @Valid @RequestBody(required = false) PurchaseOrderDecisionRequest request) {
    return ResponseEntity.ok(
        purchaseOrderService.cancel(
            actors.current(), id, request != null ? request.getNotes() : null));
  }

  @PostMapping("/{id}/accept-partial-approval")
  @AuthPolicyScope(AuthPolicyScope.Policy.BUYER_SECURED)
  @Operation(
      summary = "Buyer accepts a partial loan approval",
      description =
          "The buyer agrees to cover the shortfall between the approved loan and the price in cash")
  public ResponseEntity<PurchaseOrderResponse> acceptPartialApproval(@PathVariable UUID id) {
    return ResponseEntity.ok(purchaseOrderService.acceptPartialApproval(actors.current(), id));
  }

  @PostMapping("/{id}/convert-to-cash")
  @AuthPolicyScope(AuthPolicyScope.Policy.BUYER_SECURED)
  @Operation(summary = "Buyer drops financing and continues as a cash purchase")
  public ResponseEntity<PurchaseOrderResponse> convertToCash(@PathVariable UUID id) {
    return ResponseEntity.ok(purchaseOrderService.convertToCash(actors.current(), id));
  }

  @PostMapping("/{id}/complete")
  @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
  @AuthActionScope("purchase-orders.complete")
  @Operation(
      summary = "Seller confirms payment and closes the sale",
      description = "Marks the property SOLD and rejects competing orders")
  public ResponseEntity<PurchaseOrderResponse> complete(
      @PathVariable UUID id,
      @Valid @RequestBody(required = false) PurchaseOrderDecisionRequest request) {
    return ResponseEntity.ok(
        purchaseOrderService.complete(
            actors.current(), id, request != null ? request.getPaymentReference() : null));
  }

  /** Kept on this controller so the seller view of one listing shares the actor resolution. */
  @GetMapping("/by-property/{propertyId}")
  @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
  @Operation(summary = "List purchase orders on one of my listings")
  public ResponseEntity<List<PurchaseOrderResponse>> forProperty(
      @PathVariable UUID propertyId, @RequestParam(required = false) PurchaseOrderStatus status) {
    return ResponseEntity.ok(
        purchaseOrderService.getPurchaseOrdersForProperty(actors.current(), propertyId, status));
  }
}
