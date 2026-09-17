package com.housingplatform.purchase.repository;

import com.housingplatform.purchase.domain.PropertyPurchaseOrder;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder.PurchaseOrderStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PropertyPurchaseOrderRepository
    extends JpaRepository<PropertyPurchaseOrder, UUID> {

  boolean existsByBuyerIdAndPropertyIdAndStatusIn(
      UUID buyerId, UUID propertyId, Collection<PurchaseOrderStatus> statuses);

  Page<PropertyPurchaseOrder> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId, Pageable pageable);

  Page<PropertyPurchaseOrder> findByBuyerIdAndStatusOrderByCreatedAtDesc(
      UUID buyerId, PurchaseOrderStatus status, Pageable pageable);

  List<PropertyPurchaseOrder> findByPropertyIdOrderByCreatedAtDesc(UUID propertyId);

  List<PropertyPurchaseOrder> findByPropertyIdAndStatusOrderByCreatedAtDesc(
      UUID propertyId, PurchaseOrderStatus status);

  List<PropertyPurchaseOrder> findByPropertyIdAndStatusIn(
      UUID propertyId, Collection<PurchaseOrderStatus> statuses);

  Page<PropertyPurchaseOrder> findByRealEstateCompanyIdOrderByCreatedAtDesc(
      UUID realEstateCompanyId, Pageable pageable);

  Page<PropertyPurchaseOrder> findByRealEstateCompanyIdAndStatusOrderByCreatedAtDesc(
      UUID realEstateCompanyId, PurchaseOrderStatus status, Pageable pageable);

  @Query(
      "SELECT o FROM PropertyPurchaseOrder o JOIN o.financing f "
          + "WHERE f.bankId = :bankId AND (:status IS NULL OR o.status = :status) "
          + "ORDER BY o.createdAt DESC")
  Page<PropertyPurchaseOrder> findFinancedByBank(
      @Param("bankId") UUID bankId, @Param("status") PurchaseOrderStatus status, Pageable pageable);

  @Query(
      "SELECT o FROM PropertyPurchaseOrder o JOIN o.financing f WHERE f.loanApplicationId = :loanApplicationId")
  Optional<PropertyPurchaseOrder> findByLoanApplicationId(
      @Param("loanApplicationId") UUID loanApplicationId);

  List<PropertyPurchaseOrder> findByStatusAndExpiresAtBefore(
      PurchaseOrderStatus status, LocalDateTime cutoff);
}
