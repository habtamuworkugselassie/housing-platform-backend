package com.housingplatform.media.domain;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.property.domain.Property;
import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "media_attachments")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class MediaAttachment extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "property_id")
  private Property property;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id")
  private Organization organization;

  @Column(name = "image_url")
  private String imageUrl;

  @Basic(fetch = FetchType.LAZY)
  @Column(name = "file_data", columnDefinition = "BYTEA")
  private byte[] fileData;

  @Column(name = "content_type", length = 100)
  private String contentType;

  @Column(name = "file_name", length = 255)
  private String fileName;

  private String caption;

  @Column(nullable = false)
  private Integer displayOrder;

  @Column(nullable = false)
  @Builder.Default
  private Boolean isPrimary = false;

  @Enumerated(EnumType.STRING)
  @Column(name = "media_kind", nullable = false, length = 50)
  @Builder.Default
  private MediaKind mediaKind = MediaKind.IMAGE;

  public boolean hasFileData() {
    return fileData != null && fileData.length > 0;
  }

  public enum MediaKind {
    IMAGE,
    VIDEO,
    LOGO
  }
}
