package com.housingplatform.construction.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "project_documents")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ProjectDocument extends BaseAuditEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "project_id", nullable = false)
  private ConstructionProject project;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "phase_id")
  private ConstructionPhase phase;

  @Column(nullable = false)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "file_name", nullable = false)
  private String fileName;

  @Lob
  @Basic(fetch = FetchType.LAZY)
  @Column(name = "file_data", columnDefinition = "BYTEA")
  private byte[] fileData;

  @Column(name = "content_type", length = 100)
  private String contentType;

  @Column(name = "file_size")
  private Long fileSize;

  @Column(name = "file_url", length = 500)
  private String fileUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "document_type", nullable = false)
  @Builder.Default
  private DocumentType documentType = DocumentType.OTHER;

  @Column(name = "uploaded_by", nullable = false)
  private UUID uploadedBy;

  @Column(name = "uploaded_at", nullable = false)
  @Builder.Default
  private java.time.LocalDateTime uploadedAt = java.time.LocalDateTime.now();

  @Column(name = "version_number")
  @Builder.Default
  private Integer versionNumber = 1;

  @Column(name = "is_latest", nullable = false)
  @Builder.Default
  private Boolean isLatest = true;

  @Column(columnDefinition = "TEXT")
  private String tags;

  public enum DocumentType {
    PLAN,
    DRAWING,
    PERMIT,
    CONTRACT,
    INVOICE,
    REPORT,
    PHOTO,
    VIDEO,
    OTHER
  }

  public boolean hasFileData() {
    return fileData != null && fileData.length > 0;
  }
}
