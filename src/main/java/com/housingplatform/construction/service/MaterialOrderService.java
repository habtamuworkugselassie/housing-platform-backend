package com.housingplatform.construction.service;

import com.housingplatform.construction.domain.MaterialOrder;
import com.housingplatform.construction.dto.MaterialOrderRequest;
import com.housingplatform.construction.dto.MaterialOrderResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MaterialOrderService {
  MaterialOrderResponse createOrder(UUID userId, MaterialOrderRequest request);

  MaterialOrderResponse getOrderById(UUID id);

  MaterialOrderResponse getOrderByOrderNumber(String orderNumber);

  Page<MaterialOrderResponse> getOrdersBySupplier(
      UUID supplierId, MaterialOrder.OrderStatus status, Pageable pageable);

  Page<MaterialOrderResponse> getOrdersByProject(UUID projectId, Pageable pageable);

  MaterialOrderResponse updateOrderStatus(UUID orderId, MaterialOrder.OrderStatus status);

  MaterialOrderResponse updateOrderDelivery(UUID orderId, java.time.LocalDate deliveryDate);

  MaterialOrderResponse receiveOrderItems(
      UUID orderId, UUID itemId, java.math.BigDecimal receivedQuantity);

  void deleteOrder(UUID userId, UUID orderId);

  String generateOrderNumber();
}
