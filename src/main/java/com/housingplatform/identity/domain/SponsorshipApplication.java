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
