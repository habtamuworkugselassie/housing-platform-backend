package com.housingplatform.construction.service;

import com.housingplatform.construction.dto.BillOfQuantitiesRequest;
import com.housingplatform.construction.dto.BillOfQuantitiesResponse;

import java.util.List;
import java.util.UUID;

public interface BillOfQuantitiesService {
    BillOfQuantitiesResponse createBoQ(BillOfQuantitiesRequest request);
    BillOfQuantitiesResponse getBoQById(UUID id);
    List<BillOfQuantitiesResponse> getAllBoQs(UUID propertyId, UUID projectId);
    BillOfQuantitiesResponse updateBoQ(UUID id, BillOfQuantitiesRequest request);
    void deleteBoQ(UUID id);
}
