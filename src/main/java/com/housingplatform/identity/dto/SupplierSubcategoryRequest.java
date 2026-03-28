package com.housingplatform.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SupplierSubcategoryRequest {

  @NotBlank(message = "Name is required")
  private String name;

  /** URL-safe code; generated from name when omitted. */
  private String slug;

  private Integer sortOrder;

  private Boolean active;
}
