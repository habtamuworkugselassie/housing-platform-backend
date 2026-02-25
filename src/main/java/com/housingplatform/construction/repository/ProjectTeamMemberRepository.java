package com.housingplatform.construction.repository;

import com.housingplatform.construction.domain.ProjectTeamMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectTeamMemberRepository extends JpaRepository<ProjectTeamMember, UUID> {

  @Query(
      "SELECT tm FROM ProjectTeamMember tm WHERE tm.project.id = :projectId AND tm.status = 'ACTIVE'")
  List<ProjectTeamMember> findActiveMembersByProjectId(@Param("projectId") UUID projectId);

  @Query("SELECT tm FROM ProjectTeamMember tm WHERE tm.userId = :userId AND tm.status = 'ACTIVE'")
  List<ProjectTeamMember> findActiveProjectsByUserId(@Param("userId") UUID userId);

  @Query(
      "SELECT tm FROM ProjectTeamMember tm WHERE tm.project.id = :projectId AND tm.userId = :userId")
  Optional<ProjectTeamMember> findByProjectIdAndUserId(
      @Param("projectId") UUID projectId, @Param("userId") UUID userId);

  @Query(
      "SELECT tm FROM ProjectTeamMember tm WHERE tm.phase.id = :phaseId AND tm.status = 'ACTIVE'")
  List<ProjectTeamMember> findActiveMembersByPhaseId(@Param("phaseId") UUID phaseId);

  @Query("SELECT tm FROM ProjectTeamMember tm WHERE tm.project.id = :projectId AND tm.role = :role")
  List<ProjectTeamMember> findByProjectIdAndRole(
      @Param("projectId") UUID projectId, @Param("role") ProjectTeamMember.TeamRole role);
}
