package com.housingplatform.construction.repository;

import com.housingplatform.construction.domain.ProjectIssue;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectIssueRepository extends JpaRepository<ProjectIssue, UUID> {

  @Query("SELECT i FROM ProjectIssue i WHERE i.project.id = :projectId ORDER BY i.createdAt DESC")
  List<ProjectIssue> findByProjectIdOrderByCreatedAtDesc(@Param("projectId") UUID projectId);

  @Query("SELECT i FROM ProjectIssue i WHERE i.phase.id = :phaseId ORDER BY i.createdAt DESC")
  List<ProjectIssue> findByPhaseIdOrderByCreatedAtDesc(@Param("phaseId") UUID phaseId);

  @Query("SELECT i FROM ProjectIssue i WHERE i.project.id = :projectId AND i.status = :status")
  List<ProjectIssue> findByProjectIdAndStatus(
      @Param("projectId") UUID projectId, @Param("status") ProjectIssue.IssueStatus status);

  @Query(
      "SELECT i FROM ProjectIssue i WHERE i.assignedTo = :userId AND i.status != 'RESOLVED' AND i.status != 'CLOSED' AND i.status != 'CANCELLED'")
  List<ProjectIssue> findActiveIssuesByAssignedUser(@Param("userId") UUID userId);

  @Query("SELECT i FROM ProjectIssue i WHERE i.project.id = :projectId AND i.severity = :severity")
  List<ProjectIssue> findByProjectIdAndSeverity(
      @Param("projectId") UUID projectId, @Param("severity") ProjectIssue.IssueSeverity severity);

  @Query("SELECT i FROM ProjectIssue i WHERE i.project.id = :projectId AND i.type = :type")
  List<ProjectIssue> findByProjectIdAndType(
      @Param("projectId") UUID projectId, @Param("type") ProjectIssue.IssueType type);
}
