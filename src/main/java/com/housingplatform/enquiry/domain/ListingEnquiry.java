package com.housingplatform.enquiry.domain;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.property.domain.Property;
import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/** A lead left on a property or organization page by a visitor (no account needed). */
@Entity
@Table(name = "listing_enquiry")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ListingEnquiry extends BaseEntity {

  public enum SubjectType {
    PROPERTY,
    ORGANIZATION
  }

  public enum Status {
    NEW,
    CONTACTED,
    CLOSED
  }

  @Enumerated(EnumType.STRING)
  @Column(name = "subject_type", nullable = false, length = 32)
  private SubjectType subjectType;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "property_id")
  private Property property;

  /** The company that should answer: the organization page, or the property's lister. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id")
  private Organization organization;

  /** Snapshot of what was asked about, kept even if the listing is later deleted. */
  @Column(name = "subject_title", length = 500)
  private String subjectTitle;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(nullable = false)
  private String email;

  @Column(name = "phone_number", length = 50)
  private String phoneNumber;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String message;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  @Builder.Default
  private Status status = Status.NEW;

  @Column(name = "page_url", length = 1000)
  private String pageUrl;
}
