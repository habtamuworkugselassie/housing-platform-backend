package com.housingplatform.banking.service.impl;

import com.housingplatform.banking.domain.FinancingOffer;
import com.housingplatform.banking.dto.FinancingOfferRequest;
import com.housingplatform.banking.dto.FinancingOfferResponse;
import com.housingplatform.banking.repository.CreditProductRepository;
import com.housingplatform.banking.repository.FinancingOfferRepository;
import com.housingplatform.banking.service.FinancingOfferMapper;
import com.housingplatform.banking.service.FinancingOfferService;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class FinancingOfferServiceImpl implements FinancingOfferService {

  private final FinancingOfferRepository financingOfferRepository;
  private final FinancingOfferMapper financingOfferMapper;
  private final CreditProductRepository creditProductRepository;

  @Override
  public FinancingOfferResponse createFinancingOffer(UUID bankId, FinancingOfferRequest request) {
    FinancingOffer offer = financingOfferMapper.toEntity(request);
    offer.setBankId(bankId);
    offer.setStatus(FinancingOffer.FinancingOfferStatus.PENDING_APPROVAL);
    FinancingOffer saved = financingOfferRepository.save(offer);
    return financingOfferMapper.toResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public FinancingOfferResponse getFinancingOfferById(UUID id) {
    FinancingOffer offer =
        financingOfferRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("FinancingOffer", id));
    FinancingOfferResponse response = financingOfferMapper.toResponse(offer);
    enrichWithCreditProductDetails(response);
    return response;
  }

  @Override
  @Transactional(readOnly = true)
  public List<FinancingOfferResponse> getFinancingOffersByBankId(UUID bankId) {
    return financingOfferRepository.findByBankId(bankId).stream()
        .map(financingOfferMapper::toResponse)
        .map(this::enrichWithCreditProductDetails)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<FinancingOfferResponse> getFinancingOffersByPropertyId(UUID propertyId) {
    return financingOfferRepository
        .findByPropertyIdAndStatus(propertyId, FinancingOffer.FinancingOfferStatus.ACTIVE)
        .stream()
        .map(financingOfferMapper::toResponse)
        .map(this::enrichWithCreditProductDetails)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<FinancingOfferResponse> getFinancingOffersByProductId(UUID productId) {
    return financingOfferRepository
        .findByCreditProductIdAndStatus(productId, FinancingOffer.FinancingOfferStatus.ACTIVE)
        .stream()
        .map(financingOfferMapper::toResponse)
        .map(this::enrichWithCreditProductDetails)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<FinancingOfferResponse> getFinancingOffersByBuildingId(UUID buildingId) {
    return financingOfferRepository
        .findByBuildingIdAndStatus(buildingId, FinancingOffer.FinancingOfferStatus.ACTIVE)
        .stream()
        .map(financingOfferMapper::toResponse)
        .map(this::enrichWithCreditProductDetails)
        .collect(Collectors.toList());
  }

  @Override
  public FinancingOfferResponse linkCreditProductToProperty(
      UUID bankId, UUID creditProductId, UUID propertyId) {
    // Check if offer already exists
    List<FinancingOffer> existing =
        financingOfferRepository.findByPropertyId(propertyId).stream()
            .filter(
                offer ->
                    offer.getCreditProductId().equals(creditProductId)
                        && offer.getBankId().equals(bankId))
            .collect(Collectors.toList());

    if (!existing.isEmpty()) {
      // Update existing offer to ACTIVE if inactive
      FinancingOffer offer = existing.get(0);
      if (offer.getStatus() != FinancingOffer.FinancingOfferStatus.ACTIVE) {
        offer.setStatus(FinancingOffer.FinancingOfferStatus.ACTIVE);
        offer = financingOfferRepository.save(offer);
      }
      return financingOfferMapper.toResponse(offer);
    }

    // Create new offer
    FinancingOffer offer =
        FinancingOffer.builder()
            .bankId(bankId)
            .creditProductId(creditProductId)
            .propertyId(propertyId)
            .status(FinancingOffer.FinancingOfferStatus.ACTIVE)
            .build();

    FinancingOffer saved = financingOfferRepository.save(offer);
    return financingOfferMapper.toResponse(saved);
  }

  @Override
  public FinancingOfferResponse createPropertyCreditOffer(
      UUID bankId, UUID propertyId, BigDecimal coveragePercentage) {
    List<com.housingplatform.banking.domain.CreditProduct> activeProducts =
        creditProductRepository.findByBankIdAndStatus(
            bankId, com.housingplatform.banking.domain.CreditProduct.CreditProductStatus.ACTIVE);

    BigDecimal ltvRatio =
        coveragePercentage.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    com.housingplatform.banking.domain.CreditProduct selectedProduct = null;

    // Look for an existing product with matching max LTV
    for (com.housingplatform.banking.domain.CreditProduct product : activeProducts) {
      if (product.getMaxLoanToValueRatio() != null
          && product.getMaxLoanToValueRatio().compareTo(ltvRatio) == 0) {
        selectedProduct = product;
        break;
      }
    }

    // If no matching product is found, create a new simplified one
    if (selectedProduct == null) {
      com.housingplatform.banking.domain.CreditProduct newProduct =
          new com.housingplatform.banking.domain.CreditProduct();
      newProduct.setBankId(bankId);
      newProduct.setName("Simplified Credit Product - " + coveragePercentage + "% Coverage");
      newProduct.setDescription("Auto-generated credit product for property financing offers.");
      newProduct.setProductType(
          com.housingplatform.banking.domain.CreditProduct.CreditProductType.HOME_PURCHASE);
      newProduct.setInterestRate(BigDecimal.ZERO);
      newProduct.setMinTenureMonths(12);
      newProduct.setMaxTenureMonths(360);
      newProduct.setMaxLoanToValueRatio(ltvRatio);
      newProduct.setMinLoanAmount(BigDecimal.ZERO);
      newProduct.setMaxLoanAmount(new BigDecimal("100000000.00"));
      newProduct.setCurrency(com.housingplatform.shared.domain.Currency.ETB);
      newProduct.setStatus(
          com.housingplatform.banking.domain.CreditProduct.CreditProductStatus.ACTIVE);

      selectedProduct = creditProductRepository.save(newProduct);
    }

    FinancingOfferResponse linked =
        linkCreditProductToProperty(bankId, selectedProduct.getId(), propertyId);

    FinancingOfferRequest updateRequest = new FinancingOfferRequest();
    updateRequest.setCreditProductId(selectedProduct.getId());
    updateRequest.setPropertyId(propertyId);
    updateRequest.setSpecialLTVRatio(ltvRatio);

    return updateFinancingOffer(bankId, linked.getId(), updateRequest);
  }

  @Override
  public FinancingOfferResponse linkCreditProductToBuilding(
      UUID bankId, UUID creditProductId, UUID buildingId) {
    // Check if offer already exists
    List<FinancingOffer> existing =
        financingOfferRepository.findByBuildingId(buildingId).stream()
            .filter(
                offer ->
                    offer.getCreditProductId().equals(creditProductId)
                        && offer.getBankId().equals(bankId))
            .collect(Collectors.toList());

    if (!existing.isEmpty()) {
      // Update existing offer to ACTIVE if inactive
      FinancingOffer offer = existing.get(0);
      if (offer.getStatus() != FinancingOffer.FinancingOfferStatus.ACTIVE) {
        offer.setStatus(FinancingOffer.FinancingOfferStatus.ACTIVE);
        offer = financingOfferRepository.save(offer);
      }
      return financingOfferMapper.toResponse(offer);
    }

    // Create new offer
    FinancingOffer offer =
        FinancingOffer.builder()
            .bankId(bankId)
            .creditProductId(creditProductId)
            .buildingId(buildingId)
            .status(FinancingOffer.FinancingOfferStatus.ACTIVE)
            .build();

    FinancingOffer saved = financingOfferRepository.save(offer);
    FinancingOfferResponse response = financingOfferMapper.toResponse(saved);
    enrichWithCreditProductDetails(response);
    return response;
  }

  @Override
  public FinancingOfferResponse updateFinancingOffer(
      UUID bankId, UUID offerId, FinancingOfferRequest request) {
    FinancingOffer offer =
        financingOfferRepository
            .findById(offerId)
            .orElseThrow(() -> new ResourceNotFoundException("FinancingOffer", offerId));

    if (!offer.getBankId().equals(bankId)) {
      throw new IllegalArgumentException("Financing offer does not belong to the specified bank");
    }

    financingOfferMapper.updateEntity(offer, request);
    FinancingOffer updated = financingOfferRepository.save(offer);
    FinancingOfferResponse response = financingOfferMapper.toResponse(updated);
    enrichWithCreditProductDetails(response);
    return response;
  }

  @Override
  public void deleteFinancingOffer(UUID bankId, UUID offerId) {
    FinancingOffer offer =
        financingOfferRepository
            .findById(offerId)
            .orElseThrow(() -> new ResourceNotFoundException("FinancingOffer", offerId));

    if (!offer.getBankId().equals(bankId)) {
      throw new IllegalArgumentException("Financing offer does not belong to the specified bank");
    }

    financingOfferRepository.delete(offer);
  }

  private FinancingOfferResponse enrichWithCreditProductDetails(FinancingOfferResponse response) {
    if (response.getCreditProductId() != null) {
      creditProductRepository
          .findById(response.getCreditProductId())
          .ifPresent(
              product -> {
                response.setCreditProductName(product.getName());
                response.setCreditProductDescription(product.getDescription());
                response.setInterestRate(product.getInterestRate());
                response.setMaxLoanAmount(product.getMaxLoanAmount());
                response.setMaxTenureMonths(product.getMaxTenureMonths());
                response.setMaxLoanToValueRatio(product.getMaxLoanToValueRatio());
              });
    }
    return response;
  }
}
