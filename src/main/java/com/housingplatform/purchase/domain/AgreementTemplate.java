package com.housingplatform.purchase.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * A versioned agreement text. Templates are never edited in place: a change is a new version, and
 * signed agreements keep the exact text and version they were signed against.
 */
@Entity
@Table(name = "agreement_templates")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class AgreementTemplate extends BaseAuditEntity {

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private AgreementType type;

  /** Business version of the text; the optimistic-lock counter lives in BaseEntity.version. */
  @Column(name = "template_version", nullable = false)
  private Integer templateVersion;

  @Column(nullable = false)
  private String title;

  /** Markdown with {{placeholders}}; see {@code AgreementTemplateRenderer}. */
  @Column(nullable = false, columnDefinition = "TEXT")
  private String body;

  /** When in the order lifecycle an agreement from this template is issued. */
  @Enumerated(EnumType.STRING)
  @Column(name = "issue_trigger", nullable = false, length = 32)
  private IssueTrigger issueTrigger;

  /** Which orders the template applies to. */
  @Enumerated(EnumType.STRING)
  @Column(name = "applies_to", nullable = false, length = 16)
  @Builder.Default
  private AppliesTo appliesTo = AppliesTo.ALL;

  /** Order in which agreements are presented when several are issued at once. */
  @Column(nullable = false)
  @Builder.Default
  private Integer sequence = 0;

  /** If true the seller cannot complete the sale until the agreement is fully signed. */
  @Column(name = "blocks_completion", nullable = false)
  @Builder.Default
  private Boolean blocksCompletion = true;

  @Column(nullable = false)
  @Builder.Default
  private Boolean active = false;

  public enum AgreementType {
    PROMISE_TO_PURCHASE,
    SALE_AGREEMENT,
    FINANCING_ACKNOWLEDGEMENT,
    RESERVATION_DEPOSIT_TERMS,
    HANDOVER_AGREEMENT,
    OTHER
  }

  public enum IssueTrigger {
    /** Issued and signed inside the create-order call. */
    ORDER_CREATION,
    SELLER_ACCEPTANCE,
    FINANCING_APPROVAL,
    /** Issued by an admin through the admin endpoint. */
    MANUAL
  }

  public enum AppliesTo {
    ALL,
    CASH_ONLY,
    FINANCED_ONLY
  }
}
