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

  /**
   * Campaign attribution, captured by the browser from the URL the visitor first arrived on and
   * posted with the form. Stored on the lead rather than inferred later because the landing URL is
   * gone by the time an admin looks at the row, and because Google Analytics only knows about
   * visitors who accepted cookies — this is the copy that exists for everyone.
   */
  @Column(name = "utm_source", length = 120)
  private String utmSource;

  @Column(name = "utm_medium", length = 120)
  private String utmMedium;

  @Column(name = "utm_campaign", length = 180)
  private String utmCampaign;

  @Column(name = "utm_term", length = 180)
  private String utmTerm;

  @Column(name = "utm_content", length = 180)
  private String utmContent;

  /** Referring URL of the first page in the visit, when the browser supplied one. */
  @Column(length = 500)
  private String referrer;

  /** Path of the first page in the visit — which page actually earned the registration. */
  @Column(name = "landing_path", length = 500)
  private String landingPath;

  /**
   * Opaque token behind the unsubscribe link in every reminder. Random and per-registrant, so the
   * link cannot be guessed from an email address and unsubscribing needs no account.
   */
  @Column(name = "unsubscribe_token", length = 64, unique = true)
  private String unsubscribeToken;

  /** Set when the registrant opts out. Suppresses reminders; transactional mail still sends. */
  @Column(name = "unsubscribed_at")
  private LocalDateTime unsubscribedAt;

  public boolean isUnsubscribed() {
    return unsubscribedAt != null;
  }
}
