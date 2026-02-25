package com.housingplatform.construction.repository;

import com.housingplatform.construction.domain.ConstructionPhase;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ConstructionPhaseRepository extends JpaRepository<ConstructionPhase, UUID> {

  List<ConstructionPhase> findByProjectIdOrderBySequenceAsc(UUID projectId);

  List<ConstructionPhase> findByProjectIdAndStatus(
      UUID projectId, ConstructionPhase.PhaseStatus status);

  @Query(
      "SELECT p FROM ConstructionPhase p WHERE p.project.id = :projectId ORDER BY p.sequence ASC")
  List<ConstructionPhase> findPhasesByProjectId(@Param("projectId") UUID projectId);
}
