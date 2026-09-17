package com.housingplatform.purchase.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * One agreement between the buyer and the provider on a specific purchase order. The rendered text
 * and its hash are frozen at issue time so what was signed can always be reproduced.
 */
@Entity
@Table(name = "purchase_agreements")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PurchaseAgreement extends BaseAuditEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "purchase_order_id", nullable = false)
  private PropertyPurchaseOrder purchaseOrder;

  @Column(name = "template_id", nullable = false)
  private UUID templateId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private AgreementTemplate.AgreementType type;

  @Column(name = "template_version", nullable = false)
  private Integer templateVersion;

  @Column(nullable = false)
  private Integer sequence;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  /** SHA-256 of {@link #content}, hex. */
  @Column(name = "content_hash", nullable = false, length = 64)
  private String contentHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private AgreementStatus status;

  @Column(name = "blocks_completion", nullable = false)
  private Boolean blocksCompletion;

  @Column(name = "issued_at", nullable = false)
  private LocalDateTime issuedAt;

  // --- buyer signature evidence

  @Column(name = "buyer_user_id", nullable = false)
  private UUID buyerUserId;

  @Column(name = "buyer_signatory_name")
  private String buyerSignatoryName;

  @Enumerated(EnumType.STRING)
  @Column(name = "buyer_signature_method", length = 32)
  private SignatureMethod buyerSignatureMethod;

  @Column(name = "buyer_signed_at")
  private LocalDateTime buyerSignedAt;

  @Column(name = "buyer_signature_ip", length = 64)
  private String buyerSignatureIp;

  @Column(name = "buyer_signature_user_agent", length = 512)
  private String buyerSignatureUserAgent;

  // --- provider signature evidence

  @Column(name = "provider_name", nullable = false)
  private String providerName;

  @Column(name = "provider_signatory_name")
  private String providerSignatoryName;

  @Column(name = "provider_signatory_title")
  private String providerSignatoryTitle;

  @Column(name = "provider_signed_at")
  private LocalDateTime providerSignedAt;

  /** User id of the admin who countersigned, or null when the provider signature was automatic. */
  @Column(name = "provider_signed_by_user_id")
  private UUID providerSignedByUserId;

  @Column(name = "voided_at")
  private LocalDateTime voidedAt;

  @Column(name = "void_reason", columnDefinition = "TEXT")
  private String voidReason;

  public boolean isFullySigned() {
    return status == AgreementStatus.FULLY_SIGNED;
  }

  public enum AgreementStatus {
    /** Issued, waiting for the buyer. */
    PENDING_BUYER_SIGNATURE,
    /** Buyer signed, provider has not countersigned yet. */
    PENDING_PROVIDER_SIGNATURE,
    FULLY_SIGNED,
    /** Cancelled before completion (order closed, financing dropped, template superseded). */
    VOID
  }

  public enum SignatureMethod {
    /** Buyer typed their full name and accepted the terms while authenticated. */
    TYPED_NAME
  }
}
