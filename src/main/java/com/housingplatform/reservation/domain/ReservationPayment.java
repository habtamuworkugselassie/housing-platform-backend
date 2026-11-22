package com.housingplatform.reservation.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.SuperBuilder;

/** One Chapa checkout for a reservation: a deposit or the balance. */
@Entity
@Table(name = "reservation_payment")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ReservationPayment extends BaseEntity {

  public enum Purpose {
    DEPOSIT,
    BALANCE
  }

  public enum Status {
    PENDING,
    PAID,
    FAILED
  }

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "reservation_id")
  private SponsorshipReservation reservation;

  @Column(name = "tx_ref", nullable = false, length = 64)
  private String txRef;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Purpose purpose;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 8)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status;

  @Column(name = "checkout_url", length = 1000)
  private String checkoutUrl;

  @Column(length = 40)
  private String method;

  @Column(name = "provider_reference", length = 120)
  private String providerReference;

  @Column(name = "failure_reason", length = 500)
  private String failureReason;

  @Column(name = "paid_at")
  private LocalDateTime paidAt;
}
