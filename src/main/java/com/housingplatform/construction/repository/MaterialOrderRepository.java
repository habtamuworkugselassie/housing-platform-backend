package com.housingplatform.construction.repository;

import com.housingplatform.construction.domain.MaterialOrder;
import java.time.LocalDate;
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
public interface MaterialOrderRepository extends JpaRepository<MaterialOrder, UUID> {

  Optional<MaterialOrder> findByOrderNumber(String orderNumber);

  Page<MaterialOrder> findBySupplierId(UUID supplierId, Pageable pageable);

  Page<MaterialOrder> findByProjectId(UUID projectId, Pageable pageable);

  Page<MaterialOrder> findByStatus(MaterialOrder.OrderStatus status, Pageable pageable);

  @Query("SELECT o FROM MaterialOrder o WHERE o.supplierId = :supplierId AND o.status = :status")
  Page<MaterialOrder> findBySupplierAndStatus(
      @Param("supplierId") UUID supplierId,
      @Param("status") MaterialOrder.OrderStatus status,
      Pageable pageable);

  @Query("SELECT o FROM MaterialOrder o WHERE o.orderDate BETWEEN :startDate AND :endDate")
  List<MaterialOrder> findByOrderDateBetween(
      @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

  @Query(
      "SELECT COUNT(o) FROM MaterialOrder o WHERE o.supplierId = :supplierId AND o.status = :status")
  Long countBySupplierAndStatus(
      @Param("supplierId") UUID supplierId, @Param("status") MaterialOrder.OrderStatus status);
}
