package com.housingplatform.identity.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "sponsorships")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Sponsorship extends BaseAuditEntity {

  @Column(nullable = false, unique = true)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SponsorshipType type;

  @Column(nullable = false, precision = 19, scale = 2)
  private java.math.BigDecimal basePrice;

  @Column(columnDefinition = "TEXT")
  private String features;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private SponsorshipStatus status = SponsorshipStatus.ACTIVE;

  @Column(columnDefinition = "TEXT")
  private String notes;

  @Enumerated(EnumType.STRING)
  @Column(name = "partner_role", nullable = false)
  @Builder.Default
  private PartnerRole partnerRole = PartnerRole.SPONSOR;

  @Enumerated(EnumType.STRING)
  @Column(name = "visibility_scope", nullable = false)
  @Builder.Default
  private VisibilityScope visibilityScope = VisibilityScope.BOTH;

  @Enumerated(EnumType.STRING)
  @Column(name = "contribution_mode", nullable = false)
  @Builder.Default
  private ContributionMode contributionMode = ContributionMode.CASH;

  public enum SponsorshipType {
    EXCLUSIVE,
    @JsonAlias({"PREMIUM"})
    PLATINUM,
    GOLD,
    SILVER,
    SPECIAL;

    /** Lower value = higher marketing tier (sort order, conflict resolution). */
    public int tierRank() {
      return switch (this) {
        case EXCLUSIVE -> 0;
        case PLATINUM -> 1;
        case GOLD -> 2;
        case SILVER -> 3;
        case SPECIAL -> 4;
      };
    }
  }

  public enum SponsorshipStatus {
    ACTIVE,
    INACTIVE
  }

  public enum PartnerRole {
    SPONSOR,
    MEDIA_PARTNER
  }

  public enum VisibilityScope {
    EXHIBITION,
    PLATFORM,
    BOTH
  }

  public enum ContributionMode {
    CASH,
    IN_KIND,
    HYBRID
  }
}
