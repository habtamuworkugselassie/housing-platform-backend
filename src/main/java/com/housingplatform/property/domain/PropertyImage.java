package com.housingplatform.property.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "property_images")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PropertyImage extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "property_id", nullable = false)
  private Property property;

  @Column(name = "image_url")
  private String imageUrl; // Optional: for external URLs

  @Basic(fetch = FetchType.LAZY)
  @Column(name = "file_data", columnDefinition = "BYTEA")
  private byte[] fileData; // File content stored in database

  @Column(name = "content_type", length = 100)
  private String contentType; // MIME type (e.g., image/jpeg, video/mp4)

  @Column(name = "file_name", length = 255)
  private String fileName; // Original filename

  private String caption;

  @Column(nullable = false)
  private Integer displayOrder;

  @Column(nullable = false)
  @Builder.Default
  private Boolean isPrimary = false;

  public boolean hasFileData() {
    return fileData != null && fileData.length > 0;
  }
}
