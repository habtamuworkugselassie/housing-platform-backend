package com.housingplatform.identity.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierSubcategoryResponse {
  private UUID id;
  private String name;
  private String slug;
  private int sortOrder;
  private boolean active;
}
