package com.housingplatform.banking.service.impl;

import com.housingplatform.banking.domain.CreditProduct;
import com.housingplatform.banking.dto.CreditProductRequest;
import com.housingplatform.banking.dto.CreditProductResponse;
import com.housingplatform.banking.repository.CreditProductRepository;
import com.housingplatform.banking.service.CreditProductMapper;
import com.housingplatform.banking.service.CreditProductService;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CreditProductServiceImpl implements CreditProductService {

  private final CreditProductRepository creditProductRepository;
  private final CreditProductMapper creditProductMapper;

  @Override
  public CreditProductResponse createCreditProduct(UUID bankId, CreditProductRequest request) {
    CreditProduct product = creditProductMapper.toEntity(request);
    // Set default currency if not provided
    if (product.getCurrency() == null) {
      product.setCurrency(com.housingplatform.shared.domain.Currency.ETB);
    }
    product.setBankId(bankId);
    product.setStatus(CreditProduct.CreditProductStatus.PENDING_APPROVAL);
    CreditProduct saved = creditProductRepository.save(product);
    return creditProductMapper.toResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(value = "creditProducts", key = "#id")
  public CreditProductResponse getCreditProductById(UUID id) {
    CreditProduct product =
        creditProductRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("CreditProduct", id));
    return creditProductMapper.toResponse(product);
  }

  @Override
  @Transactional(readOnly = true)
  public List<CreditProductResponse> getCreditProductsByBankId(UUID bankId) {
    return creditProductRepository.findByBankId(bankId).stream()
        .map(creditProductMapper::toResponse)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<CreditProductResponse> getAllActiveCreditProducts() {
    return creditProductRepository.findByStatus(CreditProduct.CreditProductStatus.ACTIVE).stream()
        .map(creditProductMapper::toResponse)
        .collect(Collectors.toList());
  }

  @Override
  @CacheEvict(value = "creditProducts", key = "#productId")
  public CreditProductResponse updateCreditProduct(
      UUID bankId, UUID productId, CreditProductRequest request) {
    CreditProduct product =
        creditProductRepository
            .findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("CreditProduct", productId));

    if (!product.getBankId().equals(bankId)) {
      throw new IllegalArgumentException("Credit product does not belong to the specified bank");
    }

    creditProductMapper.updateEntity(product, request);
    CreditProduct updated = creditProductRepository.save(product);
    return creditProductMapper.toResponse(updated);
  }

  @Override
  @CacheEvict(value = "creditProducts", key = "#productId")
  public void deleteCreditProduct(UUID bankId, UUID productId) {
    CreditProduct product =
        creditProductRepository
            .findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("CreditProduct", productId));

    if (!product.getBankId().equals(bankId)) {
      throw new IllegalArgumentException("Credit product does not belong to the specified bank");
    }

    creditProductRepository.delete(product);
  }
}
