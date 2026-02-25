package com.housingplatform.construction.repository;

import com.housingplatform.construction.domain.ConstructionProject;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ConstructionProjectRepository extends JpaRepository<ConstructionProject, UUID> {

  Page<ConstructionProject> findByRealEstateCompanyId(UUID companyId, Pageable pageable);

  List<ConstructionProject> findByPropertyId(UUID propertyId);

  List<ConstructionProject> findByBuildingId(UUID buildingId);

  Page<ConstructionProject> findByStatus(
      ConstructionProject.ProjectStatus status, Pageable pageable);

  @Query(
      "SELECT p FROM ConstructionProject p WHERE p.realEstateCompanyId = :companyId AND p.status = :status")
  Page<ConstructionProject> findByCompanyAndStatus(
      @Param("companyId") UUID companyId,
      @Param("status") ConstructionProject.ProjectStatus status,
      Pageable pageable);

  @Query("SELECT p FROM ConstructionProject p WHERE p.projectManagerId = :managerId")
  Page<ConstructionProject> findByProjectManagerId(
      @Param("managerId") UUID managerId, Pageable pageable);
}
