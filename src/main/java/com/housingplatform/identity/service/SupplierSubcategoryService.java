package com.housingplatform.identity.service;

import com.housingplatform.identity.dto.SupplierSubcategoryRequest;
import com.housingplatform.identity.dto.SupplierSubcategoryResponse;
import java.util.List;
import java.util.UUID;

public interface SupplierSubcategoryService {

  List<SupplierSubcategoryResponse> listActive();

  List<SupplierSubcategoryResponse> listAllForAdmin();

  SupplierSubcategoryResponse create(SupplierSubcategoryRequest request);

  SupplierSubcategoryResponse update(UUID id, SupplierSubcategoryRequest request);

  void delete(UUID id);
}
