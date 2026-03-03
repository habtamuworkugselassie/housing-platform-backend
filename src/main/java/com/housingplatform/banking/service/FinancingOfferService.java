package com.housingplatform.banking.service;

import com.housingplatform.banking.dto.FinancingOfferRequest;
import com.housingplatform.banking.dto.FinancingOfferResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface FinancingOfferService {
  FinancingOfferResponse createFinancingOffer(UUID bankId, FinancingOfferRequest request);

  FinancingOfferResponse getFinancingOfferById(UUID id);

  List<FinancingOfferResponse> getFinancingOffersByBankId(UUID bankId);

  List<FinancingOfferResponse> getFinancingOffersByPropertyId(UUID propertyId);

  List<FinancingOfferResponse> getFinancingOffersByBuildingId(UUID buildingId);

  List<FinancingOfferResponse> getFinancingOffersByProductId(UUID productId);

  FinancingOfferResponse linkCreditProductToProperty(
      UUID bankId, UUID creditProductId, UUID propertyId);

  FinancingOfferResponse createPropertyCreditOffer(
      UUID bankId, UUID propertyId, BigDecimal coveragePercentage);

  FinancingOfferResponse linkCreditProductToBuilding(
      UUID bankId, UUID creditProductId, UUID buildingId);

  FinancingOfferResponse updateFinancingOffer(
      UUID bankId, UUID offerId, FinancingOfferRequest request);

  void deleteFinancingOffer(UUID bankId, UUID offerId);
}
