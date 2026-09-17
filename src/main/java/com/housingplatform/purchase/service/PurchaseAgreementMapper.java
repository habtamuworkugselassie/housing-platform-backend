package com.housingplatform.purchase.service;

import com.housingplatform.purchase.domain.AgreementTemplate;
import com.housingplatform.purchase.domain.PurchaseAgreement;
import com.housingplatform.purchase.dto.AgreementTemplateResponse;
import com.housingplatform.purchase.dto.PurchaseAgreementResponse;
import org.springframework.stereotype.Component;

@Component
public class PurchaseAgreementMapper {

  public PurchaseAgreementResponse toResponse(PurchaseAgreement a, boolean withContent) {
    return PurchaseAgreementResponse.builder()
        .id(a.getId())
        .purchaseOrderId(a.getPurchaseOrder() != null ? a.getPurchaseOrder().getId() : null)
        .templateId(a.getTemplateId())
        .type(a.getType())
        .templateVersion(a.getTemplateVersion())
        .sequence(a.getSequence())
        .title(a.getTitle())
        .content(withContent ? a.getContent() : null)
        .contentHash(a.getContentHash())
        .status(a.getStatus())
        .blocksCompletion(a.getBlocksCompletion())
        .issuedAt(a.getIssuedAt())
        .buyerSignatoryName(a.getBuyerSignatoryName())
        .buyerSignatureMethod(a.getBuyerSignatureMethod())
        .buyerSignedAt(a.getBuyerSignedAt())
        .providerName(a.getProviderName())
        .providerSignatoryName(a.getProviderSignatoryName())
        .providerSignatoryTitle(a.getProviderSignatoryTitle())
        .providerSignedAt(a.getProviderSignedAt())
        .voidedAt(a.getVoidedAt())
        .voidReason(a.getVoidReason())
        .build();
  }

  public AgreementTemplateResponse toResponse(AgreementTemplate t) {
    return AgreementTemplateResponse.builder()
        .id(t.getId())
        .type(t.getType())
        .version(t.getTemplateVersion())
        .title(t.getTitle())
        .body(t.getBody())
        .issueTrigger(t.getIssueTrigger())
        .appliesTo(t.getAppliesTo())
        .sequence(t.getSequence())
        .blocksCompletion(t.getBlocksCompletion())
        .active(t.getActive())
        .createdAt(t.getCreatedAt())
        .updatedAt(t.getUpdatedAt())
        .build();
  }
}
