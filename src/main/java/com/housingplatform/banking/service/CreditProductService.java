package com.housingplatform.banking.service;

import com.housingplatform.banking.dto.CreditProductRequest;
import com.housingplatform.banking.dto.CreditProductResponse;

import java.util.List;
import java.util.UUID;

public interface CreditProductService {
    CreditProductResponse createCreditProduct(UUID bankId, CreditProductRequest request);
    CreditProductResponse getCreditProductById(UUID id);
    List<CreditProductResponse> getCreditProductsByBankId(UUID bankId);
    List<CreditProductResponse> getAllActiveCreditProducts();
    CreditProductResponse updateCreditProduct(UUID bankId, UUID productId, CreditProductRequest request);
    void deleteCreditProduct(UUID bankId, UUID productId);
}
