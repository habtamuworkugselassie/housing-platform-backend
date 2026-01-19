package com.housingplatform.construction.repository;

import com.housingplatform.construction.domain.MaterialUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface MaterialUsageRepository extends JpaRepository<MaterialUsage, UUID> {
    
    List<MaterialUsage> findByProjectId(UUID projectId);
    
    List<MaterialUsage> findByPhaseId(UUID phaseId);
    
    List<MaterialUsage> findByMaterialId(UUID materialId);
    
    @Query("SELECT u FROM MaterialUsage u WHERE u.projectId = :projectId AND u.usageDate BETWEEN :startDate AND :endDate")
    List<MaterialUsage> findByProjectAndDateRange(
        @Param("projectId") UUID projectId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
    
    @Query("SELECT SUM(u.quantity) FROM MaterialUsage u WHERE u.materialId = :materialId AND u.projectId = :projectId")
    java.math.BigDecimal getTotalUsageByMaterialAndProject(
        @Param("materialId") UUID materialId,
        @Param("projectId") UUID projectId
    );
    
    Page<MaterialUsage> findByProjectId(UUID projectId, Pageable pageable);
}
