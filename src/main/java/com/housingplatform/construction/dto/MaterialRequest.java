package com.housingplatform.construction.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class MaterialRequest {

  @NotBlank(message = "Material name is required")
  private String name;

  private String description;

  @NotBlank(message = "Category is required")
  private String category;

  @NotBlank(message = "Unit is required")
  private String unit;

  @NotNull(message = "Unit price is required")
  @DecimalMin(value = "0.0", inclusive = false, message = "Unit price must be greater than 0")
  private BigDecimal unitPrice;

  @NotNull(message = "Available quantity is required")
  @Min(value = 0, message = "Available quantity must be non-negative")
  private Integer availableQuantity;

  private String brand;
  private String specifications;
}
