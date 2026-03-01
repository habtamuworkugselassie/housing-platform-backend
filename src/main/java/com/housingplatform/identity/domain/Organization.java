package com.housingplatform.identity.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Organization extends BaseAuditEntity {

  @Column(nullable = false)
  private String name;

  @Column(unique = true)
  private String registrationNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrganizationType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrganizationStatus status;

  private String address;
  private String city;
  private String country;
  private String phoneNumber;
  private String email;
  private String website;

  @Column(columnDefinition = "TEXT")
  private String description;

  @OneToOne
  @JoinColumn(name = "primary_contact_user_id")
  private User primaryContact;

  public enum OrganizationType {
    BANK,
    REAL_ESTATE_COMPANY,
    SUPPLIER,
    CONTRACTOR,
    DEVELOPER,
    INSURANCE,
    CONSULTANT,
    ARCHITECT,
    FINISHING_CONTRACTOR
  }

  public enum OrganizationStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    SUSPENDED
  }

  public enum SponsorshipType {
    NONE,
    BASIC,
    PREMIER
  }
}
