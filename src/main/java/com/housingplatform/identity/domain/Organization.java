package com.housingplatform.identity.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
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

  @OneToMany(
      mappedBy = "organization",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("displayOrder ASC")
  @Builder.Default
  private List<OrganizationPhone> phones = new ArrayList<>();

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
    CONSULTANT_ARCHITECT,
    FINISHING_CONTRACTOR;

    @JsonCreator
    public static OrganizationType fromValue(String rawValue) {
      if (rawValue == null) {
        return null;
      }
      String normalized = rawValue.trim().toUpperCase();
      if ("CONSULTANT".equals(normalized) || "ARCHITECT".equals(normalized)) {
        return CONSULTANT_ARCHITECT;
      }
      return OrganizationType.valueOf(normalized);
    }
  }

  public enum OrganizationStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    SUSPENDED
  }

  public enum SponsorshipType {
    NONE,
    GOLD,
    PREMIUM,
    SILVER
  }
}
