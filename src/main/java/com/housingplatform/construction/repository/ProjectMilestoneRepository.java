package com.housingplatform.construction.repository;

import com.housingplatform.construction.domain.ProjectMilestone;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectMilestoneRepository extends JpaRepository<ProjectMilestone, UUID> {

  @Query(
      "SELECT m FROM ProjectMilestone m WHERE m.project.id = :projectId ORDER BY m.targetDate ASC")
  List<ProjectMilestone> findByProjectIdOrderByTargetDateAsc(@Param("projectId") UUID projectId);

  @Query("SELECT m FROM ProjectMilestone m WHERE m.phase.id = :phaseId ORDER BY m.targetDate ASC")
  List<ProjectMilestone> findByPhaseIdOrderByTargetDateAsc(@Param("phaseId") UUID phaseId);

  @Query("SELECT m FROM ProjectMilestone m WHERE m.project.id = :projectId AND m.status = :status")
  List<ProjectMilestone> findByProjectIdAndStatus(
      @Param("projectId") UUID projectId, @Param("status") ProjectMilestone.MilestoneStatus status);

  @Query("SELECT m FROM ProjectMilestone m WHERE m.project.id = :projectId AND m.isCritical = true")
  List<ProjectMilestone> findCriticalMilestonesByProjectId(@Param("projectId") UUID projectId);

  @Query(
      "SELECT m FROM ProjectMilestone m WHERE m.targetDate BETWEEN :startDate AND :endDate AND m.status != 'ACHIEVED' AND m.status != 'CANCELLED'")
  List<ProjectMilestone> findUpcomingMilestones(
      @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
