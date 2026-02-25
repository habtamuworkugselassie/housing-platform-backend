package com.housingplatform.construction.repository;

import com.housingplatform.construction.domain.ProjectTask;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectTaskRepository extends JpaRepository<ProjectTask, UUID> {

  @Query(
      "SELECT t FROM ProjectTask t WHERE t.phase.id = :phaseId ORDER BY t.sequence ASC, t.createdAt ASC")
  List<ProjectTask> findByPhaseIdOrderBySequenceAsc(@Param("phaseId") UUID phaseId);

  @Query("SELECT t FROM ProjectTask t WHERE t.phase.project.id = :projectId")
  List<ProjectTask> findByProjectId(@Param("projectId") UUID projectId);

  @Query(
      "SELECT t FROM ProjectTask t WHERE t.assignedTo = :userId AND t.status != 'COMPLETED' AND t.status != 'CANCELLED'")
  List<ProjectTask> findActiveTasksByAssignedUser(@Param("userId") UUID userId);

  @Query(
      "SELECT t FROM ProjectTask t WHERE t.parentTask.id = :parentTaskId ORDER BY t.sequence ASC")
  List<ProjectTask> findSubtasksByParentId(@Param("parentTaskId") UUID parentTaskId);

  @Query("SELECT t FROM ProjectTask t WHERE t.phase.id = :phaseId AND t.status = :status")
  List<ProjectTask> findByPhaseIdAndStatus(
      @Param("phaseId") UUID phaseId, @Param("status") ProjectTask.TaskStatus status);

  @Query(
      "SELECT COUNT(t) FROM ProjectTask t WHERE t.phase.id = :phaseId AND t.status = 'COMPLETED'")
  long countCompletedTasksByPhaseId(@Param("phaseId") UUID phaseId);

  @Query("SELECT COUNT(t) FROM ProjectTask t WHERE t.phase.id = :phaseId")
  long countTotalTasksByPhaseId(@Param("phaseId") UUID phaseId);
}
