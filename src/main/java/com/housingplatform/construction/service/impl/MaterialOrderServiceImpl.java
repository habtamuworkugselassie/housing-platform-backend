package com.housingplatform.construction.service.impl;

import com.housingplatform.construction.domain.ConstructionProject;
import com.housingplatform.construction.domain.MaterialOrder;
import com.housingplatform.construction.domain.MaterialOrderItem;
import com.housingplatform.construction.dto.MaterialOrderItemResponse;
import com.housingplatform.construction.dto.MaterialOrderRequest;
import com.housingplatform.construction.dto.MaterialOrderResponse;
import com.housingplatform.construction.repository.ConstructionProjectRepository;
import com.housingplatform.construction.repository.MaterialOrderItemRepository;
import com.housingplatform.construction.repository.MaterialOrderRepository;
import com.housingplatform.construction.service.ConstructionAccessGuard;
import com.housingplatform.construction.service.MaterialOrderMapper;
import com.housingplatform.construction.service.MaterialOrderService;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MaterialOrderServiceImpl implements MaterialOrderService {

  private final MaterialOrderRepository orderRepository;
  private final MaterialOrderItemRepository orderItemRepository;
  private final MaterialOrderMapper orderMapper;
  private final OrganizationRepository organizationRepository;
  private final ConstructionProjectRepository projectRepository;
  private final ConstructionAccessGuard accessGuard;

  /**
   * Statuses the supplier an order was placed with may set: accept/fulfil the order, record that it
   * was delivered, or decline it.
   */
  private static final Set<MaterialOrder.OrderStatus> SUPPLIER_STATUSES =
      EnumSet.of(
          MaterialOrder.OrderStatus.CONFIRMED,
          MaterialOrder.OrderStatus.PROCESSING,
          MaterialOrder.OrderStatus.SHIPPED,
          MaterialOrder.OrderStatus.DELIVERED,
          MaterialOrder.OrderStatus.CANCELLED);

  /**
   * Statuses the buying side (project access, or the user who placed a project-less order) may set:
   * draft/submit, cancel, confirm receipt, or return.
   */
  private static final Set<MaterialOrder.OrderStatus> BUYER_STATUSES =
      EnumSet.of(
          MaterialOrder.OrderStatus.DRAFT,
          MaterialOrder.OrderStatus.PENDING,
          MaterialOrder.OrderStatus.CANCELLED,
          MaterialOrder.OrderStatus.DELIVERED,
          MaterialOrder.OrderStatus.RETURNED);

  /** Loads an order visible to the caller (buyer side, supplier, or admin), or throws not-found. */
  private MaterialOrder requireOrder(UUID orderId) {
    MaterialOrder order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Material Order", orderId));
    accessGuard.checkOrder(order, orderId);
    return order;
  }

  @Override
  public MaterialOrderResponse createOrder(UUID userId, MaterialOrderRequest request) {
    // Validate supplier exists and is a supplier organization
    organizationRepository
        .findById(request.getSupplierId())
        .filter(
            org ->
                org.getType()
                    == com.housingplatform.identity.domain.Organization.OrganizationType.SUPPLIER)
        .orElseThrow(() -> new BusinessException("Supplier organization not found or invalid"));

    // Validate project if provided
    ConstructionProject project = null;
    if (request.getProjectId() != null) {
      project = accessGuard.requireProject(request.getProjectId());
    }

    MaterialOrder order = orderMapper.toEntity(request);
    order.setOrderNumber(generateOrderNumber());
    order.setOrderedBy(userId);
    order.setStatus(MaterialOrder.OrderStatus.DRAFT);
    order.setProject(project);

    // Calculate subtotal from items
    BigDecimal subtotal =
        request.getItems().stream()
            .map(item -> item.getQuantity().multiply(item.getUnitPrice()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    order.setSubtotal(subtotal);

    // Calculate total
    BigDecimal total =
        subtotal
            .add(request.getTaxAmount() != null ? request.getTaxAmount() : BigDecimal.ZERO)
            .add(request.getShippingCost() != null ? request.getShippingCost() : BigDecimal.ZERO);
    order.setTotalAmount(total);

    MaterialOrder saved = orderRepository.save(order);

    // Create order items
    List<MaterialOrderItem> items =
        request.getItems().stream()
            .map(
                itemRequest -> {
                  MaterialOrderItem item = orderMapper.toItemEntity(itemRequest);
                  item.setOrder(saved);
                  item.setTotalPrice(item.getQuantity().multiply(item.getUnitPrice()));
                  return item;
                })
            .collect(Collectors.toList());

    orderItemRepository.saveAll(items);

    return enrichOrderResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public MaterialOrderResponse getOrderById(UUID id) {
    return enrichOrderResponse(requireOrder(id));
  }

  @Override
  @Transactional(readOnly = true)
  public MaterialOrderResponse getOrderByOrderNumber(String orderNumber) {
    MaterialOrder order =
        orderRepository
            .findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResourceNotFoundException("Material Order", orderNumber));
    accessGuard.checkOrder(order, orderNumber);
    return enrichOrderResponse(order);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<MaterialOrderResponse> getOrdersBySupplier(
      UUID supplierId, MaterialOrder.OrderStatus status, Pageable pageable) {
    // A supplier's order book is visible only to that supplier organization (and admins). Anyone
    // else filtering by a foreign supplierId gets an empty page rather than its orders.
    if (!accessGuard.isAdmin() && !accessGuard.isCurrentOrganization(supplierId)) {
      return Page.empty(pageable);
    }
    Page<MaterialOrder> orders;
    if (status != null) {
      orders = orderRepository.findBySupplierAndStatus(supplierId, status, pageable);
    } else {
      orders = orderRepository.findBySupplierId(supplierId, pageable);
    }
    return orders.map(this::enrichOrderResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<MaterialOrderResponse> getOrdersByProject(UUID projectId, Pageable pageable) {
    accessGuard.requireProject(projectId);
    return orderRepository.findByProjectId(projectId, pageable).map(this::enrichOrderResponse);
  }

  @Override
  public MaterialOrderResponse updateOrderStatus(UUID orderId, MaterialOrder.OrderStatus status) {
    MaterialOrder order = requireOrder(orderId);
    if (!accessGuard.isAdmin()
        && !(accessGuard.isOrderSupplier(order) && SUPPLIER_STATUSES.contains(status))
        && !(accessGuard.isOrderBuyer(order) && BUYER_STATUSES.contains(status))) {
      throw new BusinessException("You are not allowed to set this order to " + status);
    }

    order.setStatus(status);

    if (status == MaterialOrder.OrderStatus.DELIVERED && order.getActualDeliveryDate() == null) {
      order.setActualDeliveryDate(LocalDate.now());
    }

    MaterialOrder updated = orderRepository.save(order);
    return enrichOrderResponse(updated);
  }

  @Override
  public MaterialOrderResponse updateOrderDelivery(UUID orderId, LocalDate deliveryDate) {
    // Both the supplier (recording dispatch/delivery) and the buyer (confirming receipt) may do
    // this.
    MaterialOrder order = requireOrder(orderId);

    order.setActualDeliveryDate(deliveryDate);
    if (order.getStatus() != MaterialOrder.OrderStatus.DELIVERED) {
      order.setStatus(MaterialOrder.OrderStatus.DELIVERED);
    }

    MaterialOrder updated = orderRepository.save(order);
    return enrichOrderResponse(updated);
  }

  @Override
  public MaterialOrderResponse receiveOrderItems(
      UUID orderId, UUID itemId, BigDecimal receivedQuantity) {
    MaterialOrder order = requireOrder(orderId);
    // Receiving goods is a buyer-side action.
    if (!accessGuard.isAdmin() && !accessGuard.isOrderBuyer(order)) {
      throw new BusinessException("Only the ordering side can record received quantities");
    }

    MaterialOrderItem item =
        orderItemRepository
            .findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Order Item", itemId));

    if (!item.getOrder().getId().equals(orderId)) {
      throw new BusinessException("Order item does not belong to the specified order");
    }

    if (receivedQuantity.compareTo(item.getQuantity()) > 0) {
      throw new BusinessException("Received quantity cannot exceed ordered quantity");
    }

    item.setReceivedQuantity(receivedQuantity);
    orderItemRepository.save(item);

    // Update order status if all items are received
    boolean allReceived =
        order.getItems().stream()
            .allMatch(
                i ->
                    i.getReceivedQuantity() != null
                        && i.getReceivedQuantity().compareTo(i.getQuantity()) >= 0);

    if (allReceived && order.getStatus() != MaterialOrder.OrderStatus.DELIVERED) {
      order.setStatus(MaterialOrder.OrderStatus.DELIVERED);
      if (order.getActualDeliveryDate() == null) {
        order.setActualDeliveryDate(LocalDate.now());
      }
    }

    MaterialOrder updated = orderRepository.save(order);
    return enrichOrderResponse(updated);
  }

  @Override
  public void deleteOrder(UUID userId, UUID orderId) {
    MaterialOrder order = requireOrder(orderId);

    if (!order.getOrderedBy().equals(userId)) {
      throw new BusinessException("Order can only be deleted by the user who created it");
    }

    if (order.getStatus() != MaterialOrder.OrderStatus.DRAFT) {
      throw new BusinessException("Only draft orders can be deleted");
    }

    orderRepository.delete(order);
  }

  @Override
  public String generateOrderNumber() {
    String prefix = "MO";
    String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    String sequence = String.format("%04d", orderRepository.count() + 1);
    return String.format("%s-%s-%s", prefix, date, sequence);
  }

  private MaterialOrderResponse enrichOrderResponse(MaterialOrder order) {
    MaterialOrderResponse response = orderMapper.toResponse(order);

    // Enrich supplier name
    organizationRepository
        .findById(order.getSupplierId())
        .ifPresent(org -> response.setSupplierName(org.getName()));

    // Enrich order items
    List<MaterialOrderItem> items =
        orderItemRepository.findByOrderIdOrderBySequenceAsc(order.getId());
    List<MaterialOrderItemResponse> itemResponses =
        items.stream().map(orderMapper::toItemResponse).collect(Collectors.toList());
    response.setItems(itemResponses);

    return response;
  }
}
