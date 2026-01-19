package com.housingplatform.banking.repository;

import com.housingplatform.banking.domain.CreditProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CreditProductRepository extends JpaRepository<CreditProduct, UUID> {
    List<CreditProduct> findByBankId(UUID bankId);
    List<CreditProduct> findByStatus(CreditProduct.CreditProductStatus status);
    List<CreditProduct> findByBankIdAndStatus(UUID bankId, CreditProduct.CreditProductStatus status);
}
