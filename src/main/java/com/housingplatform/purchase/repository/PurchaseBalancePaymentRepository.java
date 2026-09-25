package com.housingplatform.purchase.repository;

import com.housingplatform.purchase.domain.PurchaseBalancePayment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseBalancePaymentRepository
    extends JpaRepository<PurchaseBalancePayment, UUID> {

  List<PurchaseBalancePayment> findByPurchaseOrderIdOrderByCreatedAtAsc(UUID orderId);

  Optional<PurchaseBalancePayment> findByTxRef(String txRef);

  Optional<PurchaseBalancePayment> findByIdAndPurchaseOrderId(UUID id, UUID orderId);
}
