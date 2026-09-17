package com.housingplatform.purchase.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import com.housingplatform.shared.domain.Currency;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * A buyer's order to purchase one property. The order snapshots the seller and the listed price at
 * creation time and, when the property's product catalog carries an active financing product,
 * embeds the bank-financing workflow through {@link PurchaseOrderFinancing}.
 */
@Entity
@Table(name = "property_purchase_orders")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PropertyPurchaseOrder extends BaseAuditEntity {

  @Column(name = "order_number", nullable = false, unique = true, length = 32)
  private String orderNumber;

  @Column(name = "property_id", nullable = false)
  private UUID propertyId;

  @Column(name = "buyer_id", nullable = false)
  private UUID buyerId;

  /** Snapshot of {@code Property.realEstateCompanyId} at creation. */
  @Column(name = "real_estate_company_id")
  private UUID realEstateCompanyId;

  /** Snapshot of {@code Property.agentId} at creation. */
  @Column(name = "agent_id")
  private UUID agentId;

  /** Mandatory, stored in E.164 form. */
  @Column(name = "contact_phone", nullable = false, length = 20)
  private String contactPhone;

  /** Optional, lower-cased. */
  @Column(name = "contact_email")
  private String contactEmail;

  @Enumerated(EnumType.STRING)
  @Column(name = "purchase_type", nullable = false, length = 20)
  private PurchaseType purchaseType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private PurchaseOrderStatus status;

  @Column(name = "listed_price", nullable = false, precision = 19, scale = 2)
  private BigDecimal listedPrice;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 3)
  private Currency currency;

  @Column(name = "buyer_message", columnDefinition = "TEXT")
  private String buyerMessage;

  @Column(name = "expires_at")
  private LocalDateTime expiresAt;

  @Column(name = "cancellation_reason", columnDefinition = "TEXT")
  private String cancellationReason;

  @Column(name = "rejection_reason", columnDefinition = "TEXT")
  private String rejectionReason;

  @Column(name = "payment_reference")
  private String paymentReference;

  @OneToOne(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
  private PurchaseOrderFinancing financing;

  @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("changedAt ASC")
  @Builder.Default
  private List<PurchaseOrderStatusHistory> statusHistory = new ArrayList<>();

  public boolean isFinanced() {
    return purchaseType == PurchaseType.BANK_FINANCED && financing != null;
  }

  public enum PurchaseType {
    CASH,
    BANK_FINANCED
  }

  public enum PurchaseOrderStatus {
    PENDING_SELLER_REVIEW,
    AWAITING_FINANCING,
    FINANCING_APPROVED,
    FINANCING_PARTIALLY_APPROVED,
    FINANCING_REJECTED,
    AWAITING_PAYMENT,
    COMPLETED,
    CANCELLED,
    REJECTED,
    EXPIRED;

    public static final Set<PurchaseOrderStatus> TERMINAL =
        EnumSet.of(COMPLETED, CANCELLED, REJECTED, EXPIRED);

    public static final Set<PurchaseOrderStatus> OPEN =
        EnumSet.complementOf(EnumSet.copyOf(TERMINAL));

    public boolean isTerminal() {
      return TERMINAL.contains(this);
    }
  }
}
