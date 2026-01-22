package com.housingplatform.construction.repository;

import com.housingplatform.construction.domain.ProjectDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectDocumentRepository extends JpaRepository<ProjectDocument, UUID> {
    
    @Query("SELECT d FROM ProjectDocument d WHERE d.project.id = :projectId AND d.isLatest = true ORDER BY d.uploadedAt DESC")
    List<ProjectDocument> findLatestDocumentsByProjectId(@Param("projectId") UUID projectId);
    
    @Query("SELECT d FROM ProjectDocument d WHERE d.phase.id = :phaseId AND d.isLatest = true ORDER BY d.uploadedAt DESC")
    List<ProjectDocument> findLatestDocumentsByPhaseId(@Param("phaseId") UUID phaseId);
    
    @Query("SELECT d FROM ProjectDocument d WHERE d.project.id = :projectId AND d.documentType = :documentType AND d.isLatest = true")
    List<ProjectDocument> findByProjectIdAndDocumentType(@Param("projectId") UUID projectId, @Param("documentType") ProjectDocument.DocumentType documentType);
    
    @Query("SELECT d FROM ProjectDocument d WHERE d.project.id = :projectId AND d.name = :name ORDER BY d.versionNumber DESC")
    List<ProjectDocument> findVersionsByProjectIdAndName(@Param("projectId") UUID projectId, @Param("name") String name);
}
