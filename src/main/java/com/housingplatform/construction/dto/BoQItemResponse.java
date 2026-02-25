package com.housingplatform.construction.dto;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoQItemResponse {
  private UUID id;
  private String itemName;
  private String description;
  private String unit;
  private BigDecimal quantity;
  private BigDecimal unitPrice;
  private BigDecimal totalPrice;
  private UUID materialId;
  private Integer sequence;
}
