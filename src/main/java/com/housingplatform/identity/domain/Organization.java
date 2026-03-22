package com.housingplatform.identity.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
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

  private String businessRegistration;
  private String license;
  private String vatRegistration;
  private String tinRegistration;

  /** Number/code in parallel with document URL (businessRegistration). */
  private String businessRegistrationNumber;

  /** Number in parallel with document URL (license). */
  private String licenseNumber;

  /** Number in parallel with document URL (vatRegistration). */
  private String vatNumber;

  /** Number in parallel with document URL (tinRegistration). */
  private String tinNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrganizationType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrganizationStatus status;

  private String address;
  private String city;
  private String country;

  /** HQ or branch location coordinates (from map picker). */
  private Double latitude;

  private Double longitude;

  /**
   * Email, website, social URLs, and phone numbers live in {@link OrganizationContact} (1:1).
   * Exposed flat on the JSON API via {@link
   * com.housingplatform.identity.service.OrganizationMapper}.
   */
  @OneToOne(
      mappedBy = "organization",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private OrganizationContact contact;

  @Column(columnDefinition = "TEXT")
  private String description;

  @OneToOne
  @JoinColumn(name = "primary_contact_user_id")
  private User primaryContact;

  /**
   * Verification level for badge display: FULL = all documents and numbers, HALF = one category
   * complete (e.g. all documents but numbers missing, or vice versa), NONE = neither.
   */
  public VerificationLevel getVerificationLevel() {
    boolean allDocs =
        isNonBlank(businessRegistration)
            && isNonBlank(license)
            && isNonBlank(vatRegistration)
            && isNonBlank(tinRegistration);
    boolean allNumbers =
        isNonBlank(businessRegistrationNumber)
            && isNonBlank(licenseNumber)
            && isNonBlank(vatNumber)
            && isNonBlank(tinNumber);
    if (allDocs && allNumbers) {
      return VerificationLevel.FULL;
    }
    if (allDocs || allNumbers) {
      return VerificationLevel.HALF;
    }
    return VerificationLevel.NONE;
  }

  /** True when fully verified (all 8 fields). Kept for backward compatibility. */
  public boolean isVerified() {
    return getVerificationLevel() == VerificationLevel.FULL;
  }

  private static boolean isNonBlank(String s) {
    return s != null && !s.trim().isEmpty();
  }

  public enum VerificationLevel {
    NONE,
    HALF,
    FULL
  }

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
    EXCLUSIVE,
    @JsonAlias({"PREMIUM"})
    PLATINUM,
    GOLD,
    SILVER,
    SPECIAL
  }
}
