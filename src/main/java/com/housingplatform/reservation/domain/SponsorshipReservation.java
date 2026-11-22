package com.housingplatform.reservation.domain;

import com.housingplatform.exhibition.domain.ExhibitionInterest;
import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.Sponsorship;
import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.SuperBuilder;

/** A sponsor's request for a package, tracked from the form to full payment. */
@Entity
@Table(name = "sponsorship_reservation")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SponsorshipReservation extends BaseEntity {

  public enum Status {
    REQUESTED,
    AGREEMENT_SENT,
    SIGNED,
    DEPOSIT_PAID,
    PAID,
    CANCELLED;

    /** Still in play: counts toward the pipeline. */
    public boolean open() {
      return this != CANCELLED;
    }
  }

  @Column(nullable = false, length = 20)
  private String reference;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sponsorship_id")
  private Sponsorship sponsorship;

  @Column(name = "package_name", nullable = false, length = 200)
  private String packageName;

  @Column(name = "package_type", length = 40)
  private String packageType;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 8)
  private String currency;

  @Column(nullable = false, length = 200)
  private String company;

  @Column(name = "contact_name", nullable = false, length = 200)
  private String contactName;

  @Column(nullable = false)
  private String email;

  @Column(length = 50)
  private String phone;

  @Column(length = 40)
  private String tin;

  @Column(name = "booth_preference", length = 200)
  private String boothPreference;

  @Column(columnDefinition = "TEXT")
  private String message;

  @Column(nullable = false, length = 8)
  private String locale;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private Status status;

  @Column(name = "amount_paid", nullable = false, precision = 19, scale = 2)
  private BigDecimal amountPaid;

  @Column(name = "admin_notes", columnDefinition = "TEXT")
  private String adminNotes;

  @Column(name = "assigned_to", length = 120)
  private String assignedTo;

  @Column(length = 40)
  private String source;

  @Column(name = "agreement_sent_at")
  private LocalDateTime agreementSentAt;

  @Column(name = "signed_at")
  private LocalDateTime signedAt;

  @Column(name = "paid_at")
  private LocalDateTime paidAt;

  @Column(name = "last_reminder_at")
  private LocalDateTime lastReminderAt;

  @Column(name = "reminder_count", nullable = false)
  private int reminderCount;

  @Column(name = "status_changed_at", nullable = false)
  private LocalDateTime statusChangedAt;

  /** When the money is due: set at signing (business days after), editable by an admin. */
  @Column(name = "payment_due_at")
  private LocalDateTime paymentDueAt;

  @Column(name = "payment_reminder_count", nullable = false)
  private int paymentReminderCount;

  @Column(name = "last_payment_reminder_at")
  private LocalDateTime lastPaymentReminderAt;

  public BigDecimal outstanding() {
    BigDecimal paid = amountPaid == null ? BigDecimal.ZERO : amountPaid;
    BigDecimal total = amount == null ? BigDecimal.ZERO : amount;
    return total.subtract(paid).max(BigDecimal.ZERO);
  }

  /** Signed or part-paid, past the due date, and money still outstanding. */
  public boolean overdue(LocalDateTime now) {
    return (status == Status.SIGNED || status == Status.DEPOSIT_PAID)
        && paymentDueAt != null
        && paymentDueAt.isBefore(now)
        && outstanding().signum() > 0;
  }

  /** The exhibitor account provisioned from this reservation, once a payment is in. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id")
  private Organization organization;

  @Column(name = "exhibitor_invited_at")
  private LocalDateTime exhibitorInvitedAt;

  /** The lead this reservation came from, or the one created for it. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "exhibition_interest_id")
  private ExhibitionInterest interest;
}
