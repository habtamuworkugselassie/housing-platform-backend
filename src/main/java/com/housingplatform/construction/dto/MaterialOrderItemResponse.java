package com.housingplatform.construction.dto;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;

@Data
public class MaterialOrderItemResponse {

  private UUID id;

  private UUID orderId;

  private UUID materialId;

  private String materialName;

  private String description;

  private String category;

  private String unit;

  private BigDecimal quantity;

  private BigDecimal unitPrice;

  private BigDecimal totalPrice;

  private BigDecimal receivedQuantity;

  private String brand;

  private String specifications;

  private Integer sequence;
}
