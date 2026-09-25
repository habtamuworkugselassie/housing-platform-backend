package com.housingplatform.property.dto;

import com.housingplatform.property.domain.PropertyDocument;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/** A property document without its storage location; files are fetched through /file endpoints. */
@Data
@Builder
public class PropertyDocumentResponse {
  private UUID id;
  private UUID propertyId;
  private String propertyTitle;
  private PropertyDocument.DocumentType documentType;
  private String documentTypeLabel;
  private String documentNumber;
  private String issuingAuthority;
  private LocalDate issuedOn;
  private String description;
  private String fileName;
  private String contentType;
  private Long sizeBytes;
  private String sha256;
  private PropertyDocument.DocumentStatus status;
  private String reviewNote;
  private LocalDateTime reviewedAt;
  private LocalDateTime createdAt;
}
