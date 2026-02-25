package com.housingplatform.construction.repository;

import com.housingplatform.construction.domain.MaterialOrderItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MaterialOrderItemRepository extends JpaRepository<MaterialOrderItem, UUID> {

  List<MaterialOrderItem> findByOrderIdOrderBySequenceAsc(UUID orderId);

  @Query("SELECT i FROM MaterialOrderItem i WHERE i.materialId = :materialId")
  List<MaterialOrderItem> findByMaterialId(@Param("materialId") UUID materialId);
}
