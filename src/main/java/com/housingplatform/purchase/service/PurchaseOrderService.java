package com.housingplatform.purchase.service;

import com.housingplatform.loan.domain.LoanApplication;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder.PurchaseOrderStatus;
import com.housingplatform.purchase.dto.CreatePurchaseOrderRequest;
import com.housingplatform.purchase.dto.PurchaseOrderResponse;
import com.housingplatform.purchase.dto.PurchasePreviewResponse;
import com.housingplatform.purchase.dto.UpdatePurchaseFinancingRequest;
import com.housingplatform.shared.domain.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PurchaseOrderService {

  PurchasePreviewResponse preview(PurchaseOrderActor buyer, UUID propertyId, Currency currency);

  /** Creates the order and signs the Promise to Purchase in one transaction. */
  PurchaseOrderResponse createPurchaseOrder(
      PurchaseOrderActor buyer, CreatePurchaseOrderRequest request, SignatureEvidence evidence);

  PurchaseOrderResponse getPurchaseOrder(PurchaseOrderActor actor, UUID orderId);

  Page<PurchaseOrderResponse> getMyPurchaseOrders(
      PurchaseOrderActor buyer, PurchaseOrderStatus status, Pageable pageable);

  List<PurchaseOrderResponse> getPurchaseOrdersForProperty(
      PurchaseOrderActor seller, UUID propertyId, PurchaseOrderStatus status);

  Page<PurchaseOrderResponse> getReceivedPurchaseOrders(
      PurchaseOrderActor seller, PurchaseOrderStatus status, Pageable pageable);

  Page<PurchaseOrderResponse> getFinancedPurchaseOrders(
      PurchaseOrderActor banker, PurchaseOrderStatus status, Pageable pageable);

  PurchaseOrderResponse updateFinancing(
      PurchaseOrderActor buyer, UUID orderId, UpdatePurchaseFinancingRequest request);

  PurchaseOrderResponse accept(PurchaseOrderActor seller, UUID orderId, String notes);

  PurchaseOrderResponse reject(PurchaseOrderActor seller, UUID orderId, String reason);

  PurchaseOrderResponse cancel(PurchaseOrderActor buyer, UUID orderId, String reason);

  PurchaseOrderResponse acceptPartialApproval(PurchaseOrderActor buyer, UUID orderId);

  PurchaseOrderResponse convertToCash(PurchaseOrderActor buyer, UUID orderId);

  PurchaseOrderResponse complete(PurchaseOrderActor seller, UUID orderId, String paymentReference);

  /** Moves stale PENDING_SELLER_REVIEW orders to EXPIRED. Returns how many were expired. */
  int expireStaleOrders();

  /** Mirrors a loan application's new status onto the order that opened it. */
  void applyLoanApplicationStatus(
      UUID loanApplicationId, LoanApplication.LoanApplicationStatus loanStatus);
}
