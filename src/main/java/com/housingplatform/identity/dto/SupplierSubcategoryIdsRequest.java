package com.housingplatform.identity.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class SupplierSubcategoryIdsRequest {

  /** When empty, clears all assignments for a SUPPLIER organization. */
  private List<UUID> supplierSubcategoryIds = new ArrayList<>();
}
