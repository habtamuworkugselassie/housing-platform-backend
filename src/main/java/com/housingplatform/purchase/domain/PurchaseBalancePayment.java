package com.housingplatform.purchase.domain;

import com.housingplatform.payment.chapa.ChapaPaymentMethod;
import com.housingplatform.shared.domain.BaseAuditEntity;
import com.housingplatform.shared.domain.Currency;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Money paid towards an order's balance: an online Chapa checkout, a bank transfer the buyer
 * reports with a slip, or a payment the seller/admin records. Only PAID ones count.
 */
@Entity
@Table(name = "purchase_balance_payments")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PurchaseBalancePayment extends BaseAuditEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "purchase_order_id", nullable = false)
  private PropertyPurchaseOrder purchaseOrder;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private Channel channel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private Status status;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 3)
  private Currency currency;

  /** Online: our reference at Chapa, unique per checkout. */
  @Column(name = "tx_ref", unique = true, length = 64)
  private String txRef;

  @Column(name = "checkout_url", length = 1024)
  private String checkoutUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "preferred_method", length = 24)
  private ChapaPaymentMethod preferredMethod;

  @Column(name = "provider_reference")
  private String providerReference;

  /** As reported by Chapa, e.g. "telebirr". */
  @Column(name = "payment_method", length = 64)
  private String paymentMethod;

  /** Bank transfer or recorded payment: the bank's transaction reference. */
  @Column(name = "reference")
  private String reference;

  @Column(name = "paid_on")
  private LocalDate paidOn;

  /** Private storage URL of the transfer slip; never handed to clients. */
  @Column(name = "slip_url", columnDefinition = "TEXT")
  private String slipUrl;

  @Column(name = "slip_file_name")
  private String slipFileName;

  @Column(name = "slip_content_type", length = 100)
  private String slipContentType;

  @Column(name = "submitted_by_user_id")
  private UUID submittedByUserId;

  @Column(name = "reviewed_by_user_id")
  private UUID reviewedByUserId;

  @Column(name = "reviewed_at")
  private LocalDateTime reviewedAt;

  /** Failure or rejection reason, or a reviewer's note. */
  @Column(name = "note", columnDefinition = "TEXT")
  private String note;

  @Column(name = "paid_at")
  private LocalDateTime paidAt;

  public enum Channel {
    ONLINE,
    BANK_TRANSFER,
    /** Entered by the seller or an admin for money received outside the platform. */
    RECORDED
  }

  public enum Status {
    /** Online checkout started, waiting for Chapa. */
    PENDING,
    /** Bank transfer reported, waiting for the seller or an admin to confirm. */
    SUBMITTED,
    PAID,
    FAILED,
    REJECTED,
    CANCELLED,
    /** Paid, then the order closed: money must go back. */
    REFUND_PENDING,
    REFUNDED
  }
}
