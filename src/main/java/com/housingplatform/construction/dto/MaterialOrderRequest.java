package com.housingplatform.construction.dto;

import com.housingplatform.shared.domain.Currency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class MaterialOrderRequest {

  private UUID projectId;

  @NotNull(message = "Supplier ID is required")
  private UUID supplierId;

  private LocalDate orderDate;

  private LocalDate expectedDeliveryDate;

  private BigDecimal taxAmount;

  private BigDecimal shippingCost;

  @NotNull(message = "Currency is required")
  private Currency currency;

  private String notes;

  private String deliveryAddress;

  private String deliveryCity;

  private String deliveryState;

  private String deliveryCountry;

  @NotEmpty(message = "At least one order item is required")
  @Valid
  private List<MaterialOrderItemRequest> items;
}
