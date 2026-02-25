package com.housingplatform.construction.dto;

import com.housingplatform.construction.domain.MaterialOrder;
import com.housingplatform.shared.domain.Currency;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class MaterialOrderResponse {

  private UUID id;

  private String orderNumber;

  private UUID projectId;

  private String projectName;

  private UUID supplierId;

  private String supplierName;

  private UUID orderedBy;

  private String orderedByName;

  private MaterialOrder.OrderStatus status;

  private LocalDate orderDate;

  private LocalDate expectedDeliveryDate;

  private LocalDate actualDeliveryDate;

  private BigDecimal subtotal;

  private BigDecimal taxAmount;

  private BigDecimal shippingCost;

  private BigDecimal totalAmount;

  private Currency currency;

  private String notes;

  private String deliveryAddress;

  private String deliveryCity;

  private String deliveryState;

  private String deliveryCountry;

  private List<MaterialOrderItemResponse> items;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;
}
