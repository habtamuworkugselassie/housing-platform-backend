package com.housingplatform.purchase.dto;

import com.housingplatform.purchase.domain.AgreementTemplate;
import com.housingplatform.purchase.domain.PurchaseAgreement;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseAgreementResponse {
  private UUID id;
  private UUID purchaseOrderId;
  private UUID templateId;
  private AgreementTemplate.AgreementType type;
  private Integer templateVersion;
  private Integer sequence;
  private String title;

  /** Rendered Markdown; null in list views. */
  private String content;

  private String contentHash;
  private PurchaseAgreement.AgreementStatus status;
  private Boolean blocksCompletion;
  private LocalDateTime issuedAt;
  private String buyerSignatoryName;
  private PurchaseAgreement.SignatureMethod buyerSignatureMethod;
  private LocalDateTime buyerSignedAt;
  private String providerName;
  private String providerSignatoryName;
  private String providerSignatoryTitle;
  private LocalDateTime providerSignedAt;
  private LocalDateTime voidedAt;
  private String voidReason;
}
