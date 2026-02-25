package com.housingplatform.property.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

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

  @Column(nullable = false)
  private String documentType;

  @Column(nullable = false)
  private String fileName;

  @Column(nullable = false)
  private String fileUrl;

  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DocumentStatus status;

  public enum DocumentStatus {
    PENDING,
    VERIFIED,
    REJECTED
  }
}
