package com.housingplatform.exhibition.domain;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.Sponsorship;
import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "exhibition_interest")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ExhibitionInterest extends BaseEntity {

  @Column(nullable = false)
  private String email;

  @Column(name = "phone_number", length = 50)
  private String phoneNumber;

  @Column(name = "interest_type", nullable = false, length = 50)
  private String interestType; // "exhibitor" | "visitor" | "partner"

  @Enumerated(EnumType.STRING)
  @Column(name = "partner_role", length = 32)
  private Sponsorship.PartnerRole partnerRole;

  @Enumerated(EnumType.STRING)
  @Column(name = "visibility_scope", length = 32)
  private Sponsorship.VisibilityScope visibilityScope;

  @Enumerated(EnumType.STRING)
  @Column(name = "contribution_mode", length = 32)
  private Sponsorship.ContributionMode contributionMode;

  @Column(length = 500)
  private String company;

  @Column(columnDefinition = "TEXT")
  private String message;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id")
  private Organization organization;

  /** When interest is exhibitor: which sponsorship tier they are interested in (optional FK). */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sponsorship_id")
  private Sponsorship sponsorship;

  /** Set when an admin confirms the registrant email/phone (exhibition lead workflow). */
  @Column(name = "contact_verified_at")
  private LocalDateTime contactVerifiedAt;
}
