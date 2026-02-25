package com.housingplatform.construction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;

@Data
public class BoQItemRequest {

  @NotBlank(message = "Item name is required")
  private String itemName;

  private String description;

  @NotBlank(message = "Unit is required")
  private String unit;

  @NotNull(message = "Quantity is required")
  private BigDecimal quantity;

  @NotNull(message = "Unit price is required")
  private BigDecimal unitPrice;

  private UUID materialId;
  private Integer sequence;
}
