package com.housingplatform.purchase.repository;

import com.housingplatform.purchase.domain.PurchaseBalanceInstalment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseBalanceInstalmentRepository
    extends JpaRepository<PurchaseBalanceInstalment, UUID> {

  List<PurchaseBalanceInstalment> findByPurchaseOrderIdOrderBySequenceAsc(UUID orderId);

  void deleteByPurchaseOrderId(UUID orderId);
}
