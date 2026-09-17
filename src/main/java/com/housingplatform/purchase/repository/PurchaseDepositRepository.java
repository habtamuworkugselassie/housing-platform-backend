package com.housingplatform.purchase.repository;

import com.housingplatform.purchase.domain.PurchaseDeposit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PurchaseDepositRepository extends JpaRepository<PurchaseDeposit, UUID> {
  Optional<PurchaseDeposit> findByTxRef(String txRef);

  Optional<PurchaseDeposit> findByPurchaseOrderId(UUID purchaseOrderId);
}
