package com.housingplatform.identity.dto;

import com.housingplatform.identity.domain.Organization;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationResponse {
  private UUID id;
  private String name;
  private String registrationNumber;
  private String businessRegistration;
  private String license;
  private String vatRegistration;
  private String tinRegistration;

  /** Number/code in parallel with document (businessRegistration). */
  private String businessRegistrationNumber;

  private String licenseNumber;
  private String vatNumber;
  private String tinNumber;

  private Organization.OrganizationType type;
  private Organization.OrganizationStatus status;
  private String address;
  private String city;
  private String country;

  private Double latitude;
  private Double longitude;

  /**
   * Phone numbers with country code. For backward compatibility, first phone formatted as single
   * string.
   */
  private List<OrganizationPhoneDto> phoneNumbers;

  /** First phone formatted as "countryCode number" for backward compatibility. */
  public String getPhoneNumber() {
    if (phoneNumbers == null || phoneNumbers.isEmpty()) return null;
    OrganizationPhoneDto first = phoneNumbers.get(0);
    if (first == null || first.getNumber() == null || first.getNumber().isBlank()) return null;
    String cc = first.getCountryCode() != null ? first.getCountryCode().trim() : "";
    String num = first.getNumber().trim();
    return (cc + " " + num).trim();
  }

  private String email;
  private String website;

  private String facebookUrl;
  private String instagramUrl;
  private String linkedinUrl;
  private String twitterUrl;
  private String youtubeUrl;

  private String description;
  private UUID primaryContactUserId;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** Logo URL (from media attachment with kind LOGO or primary). */
  private String logoUrl;

  /** All media attachments (logo, images, videos). */
  private List<OrganizationMediaItem> media;

  /** True when fully verified (all 8 fields). Kept for backward compatibility. */
  private Boolean verified;

  /**
   * Verification level: NONE, HALF (e.g. documents submitted but numbers missing, or vice versa),
   * FULL (all documents and numbers). Used for half vs fully verified badge on frontend and mobile.
   */
  private String verificationLevel;

  /** Returns true if fully verified (all 8 verification fields non-blank). */
  public boolean isVerified() {
    if (Boolean.TRUE.equals(verified)) return true;
    return "FULL".equals(verificationLevel);
  }

  /**
   * Active marketplace sponsorship (approved application, current date in range). Set for public
   * marketplace listings; null when not sponsored.
   */
  private Boolean isSponsored;

  /** {@link com.housingplatform.identity.domain.Sponsorship.SponsorshipType} name, e.g. GOLD. */
  private String sponsorshipType;

  /**
   * Construction material supplier specializations ({@link Organization.OrganizationType#SUPPLIER}
   * only). Empty for other types.
   */
  private List<SupplierSubcategoryResponse> supplierSubcategories;
}
