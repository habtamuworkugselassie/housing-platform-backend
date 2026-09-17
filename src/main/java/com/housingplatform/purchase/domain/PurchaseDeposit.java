package com.housingplatform.purchase.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import com.housingplatform.shared.domain.Currency;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * The reservation deposit a buyer pays to the provider after the seller accepts. Paid through the
 * payment provider's hosted checkout; this row is the platform's ledger entry for it.
 */
@Entity
@Table(name = "purchase_deposits")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PurchaseDeposit extends BaseAuditEntity {

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "purchase_order_id", nullable = false, unique = true)
  private PropertyPurchaseOrder purchaseOrder;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 3)
  private Currency currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private DepositStatus status;

  @Column(name = "due_at")
  private LocalDateTime dueAt;

  /** Our transaction reference at the provider; unique per checkout attempt. */
  @Column(name = "tx_ref", unique = true, length = 64)
  private String txRef;

  @Column(name = "checkout_url", length = 1024)
  private String checkoutUrl;

  @Column(name = "provider", nullable = false, length = 32)
  @Builder.Default
  private String provider = "CHAPA";

  @Column(name = "provider_reference")
  private String providerReference;

  /** e.g. "card", "telebirr" as reported by the provider. */
  @Column(name = "payment_method", length = 64)
  private String paymentMethod;

  @Column(name = "paid_at")
  private LocalDateTime paidAt;

  @Column(name = "failure_reason", columnDefinition = "TEXT")
  private String failureReason;

  @Column(name = "attempts", nullable = false)
  @Builder.Default
  private Integer attempts = 0;

  @Column(name = "waived_by_user_id")
  private UUID waivedByUserId;

  @Column(name = "waive_reason", columnDefinition = "TEXT")
  private String waiveReason;

  @Column(name = "refund_reference")
  private String refundReference;

  @Column(name = "refunded_at")
  private LocalDateTime refundedAt;

  public boolean isSettled() {
    return status == DepositStatus.PAID || status == DepositStatus.WAIVED;
  }

  public enum DepositStatus {
    /** Issued, nothing started. */
    DUE,
    /** Checkout started at the provider, waiting for the result. */
    PENDING,
    PAID,
    FAILED,
    /** Order closed before payment; nothing owed. */
    CANCELLED,
    /** Admin decided no deposit is needed. */
    WAIVED,
    /** Paid, then the order closed: money must go back. */
    REFUND_PENDING,
    REFUNDED
  }
}
