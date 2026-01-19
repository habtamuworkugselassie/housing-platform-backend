package com.housingplatform.identity.repository;

import com.housingplatform.identity.domain.RealEstateAgent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RealEstateAgentRepository extends JpaRepository<RealEstateAgent, UUID> {
    @Query("SELECT a FROM RealEstateAgent a WHERE a.user.id = :userId")
    Optional<RealEstateAgent> findByUserId(@Param("userId") UUID userId);
    
    @Query("SELECT a FROM RealEstateAgent a WHERE a.organization.id = :organizationId")
    List<RealEstateAgent> findByOrganizationId(@Param("organizationId") UUID organizationId);
    
    @Query("SELECT a FROM RealEstateAgent a WHERE a.organization.id = :organizationId AND a.status = :status")
    List<RealEstateAgent> findByOrganizationIdAndStatus(
            @Param("organizationId") UUID organizationId, 
            @Param("status") RealEstateAgent.AgentStatus status);
    
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM RealEstateAgent a WHERE a.user.id = :userId")
    boolean existsByUserId(@Param("userId") UUID userId);
    
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM RealEstateAgent a WHERE a.organization.id = :organizationId AND a.user.id = :userId")
    boolean existsByOrganizationIdAndUserId(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId);
    
    @Query("SELECT a FROM RealEstateAgent a WHERE a.organization.id = :organizationId AND a.isSuperAgent = true")
    Optional<RealEstateAgent> findSuperAgentByOrganizationId(@Param("organizationId") UUID organizationId);
    
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM RealEstateAgent a WHERE a.organization.id = :organizationId AND a.user.id = :userId AND a.isSuperAgent = true")
    boolean isSuperAgentOfOrganization(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId);
}
