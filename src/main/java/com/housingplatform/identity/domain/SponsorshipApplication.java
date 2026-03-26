package com.housingplatform.identity.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "sponsorship_applications")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SponsorshipApplication extends BaseAuditEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sponsorship_id", nullable = false)
  private Sponsorship sponsorship;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private ApplicationStatus status = ApplicationStatus.PENDING;

  @Column(nullable = false)
  private LocalDateTime startDate;

  @Column(nullable = false)
  private LocalDateTime endDate;

  @Column(columnDefinition = "TEXT")
  private String notes;

  @Column(columnDefinition = "TEXT")
  private String rejectionReason;

  private java.math.BigDecimal amount;

  private String paymentReference;

  /** Set when an admin verifies organization documents/details for this application. */
  private LocalDateTime organizationVerifiedAt;

  /**
   * Set when an admin verifies the primary contact (or super agent) user for this application.
   */
  private LocalDateTime userVerifiedAt;

  /**
   * When false, the organization was not yet APPROVED when this application was created (e.g.
   * exhibition exhibitor signup). Reject/cancel pending restores {@code PENDING_APPROVAL} instead
   * of {@code APPROVED}.
   */
  @Column(name = "organization_was_approved_before_application", nullable = false)
  @Builder.Default
  private Boolean organizationWasApprovedBeforeApplication = true;

  public boolean isActive() {
    if (status != ApplicationStatus.APPROVED) {
      return false;
    }
    LocalDateTime now = LocalDateTime.now();
    return !now.isBefore(startDate) && !now.isAfter(endDate);
  }

  public enum ApplicationStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
  }
}
