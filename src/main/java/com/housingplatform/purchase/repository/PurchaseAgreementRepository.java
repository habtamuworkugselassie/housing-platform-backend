package com.housingplatform.purchase.repository;

import com.housingplatform.purchase.domain.PurchaseAgreement;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PurchaseAgreementRepository extends JpaRepository<PurchaseAgreement, UUID> {
  Optional<PurchaseAgreement> findByIdAndPurchaseOrderId(UUID id, UUID purchaseOrderId);
}
