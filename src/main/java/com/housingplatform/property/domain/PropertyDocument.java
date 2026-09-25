package com.housingplatform.property.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * An official document for a property (title deed, land lease contract, permit). Uploaded by the
 * seller, verified by an admin; verified documents are listed in the Promise to Purchase. The file
 * is private: it is only served through endpoints that check who is asking.
 */
@Entity
@Table(name = "property_documents")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PropertyDocument extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "property_id", nullable = false)
  private Property property;

  /** A {@link DocumentType} name; stored as text because older rows may hold free text. */
  @Column(nullable = false)
  private String documentType;

  @Column(nullable = false)
  private String fileName;

  /** Storage URL under the private uploads folder; never handed to clients. */
  @Column(nullable = false)
  private String fileUrl;

  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DocumentStatus status;

  @Column(name = "document_number", length = 100)
  private String documentNumber;

  @Column(name = "issuing_authority")
  private String issuingAuthority;

  @Column(name = "issued_on")
  private LocalDate issuedOn;

  @Column(name = "content_type", length = 100)
  private String contentType;

  @Column(name = "size_bytes")
  private Long sizeBytes;

  /** Fingerprint of the file, quoted in agreements so the signed text points at this exact file. */
  @Column(name = "sha256", length = 64)
  private String sha256;

  @Column(name = "uploaded_by_user_id")
  private UUID uploadedByUserId;

  @Column(name = "reviewed_by_user_id")
  private UUID reviewedByUserId;

  @Column(name = "reviewed_at")
  private LocalDateTime reviewedAt;

  @Column(name = "review_note", columnDefinition = "TEXT")
  private String reviewNote;

  public enum DocumentStatus {
    PENDING,
    VERIFIED,
    REJECTED
  }

  public enum DocumentType {
    /** Title deed / certificate of ownership ("ካርታ"). */
    TITLE_DEED,
    /** Land lease contract with the city administration. */
    LEASE_AGREEMENT,
    BUILDING_PERMIT,
    OCCUPANCY_PERMIT,
    OTHER;

    public String label() {
      return switch (this) {
        case TITLE_DEED -> "Title deed / certificate of ownership";
        case LEASE_AGREEMENT -> "Land lease contract";
        case BUILDING_PERMIT -> "Building permit";
        case OCCUPANCY_PERMIT -> "Occupancy permit";
        case OTHER -> "Other document";
      };
    }

    public static DocumentType fromStored(String value) {
      try {
        return value == null ? OTHER : DocumentType.valueOf(value);
      } catch (IllegalArgumentException e) {
        return OTHER;
      }
    }
  }
}
