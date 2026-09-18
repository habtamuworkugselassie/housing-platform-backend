package com.housingplatform.purchase.service;

import com.housingplatform.property.domain.Property;
import com.housingplatform.purchase.domain.AgreementTemplate;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder;
import com.housingplatform.purchase.dto.AgreementSignatureRequest;
import com.housingplatform.purchase.dto.AgreementTemplateRequest;
import com.housingplatform.purchase.dto.AgreementTemplateResponse;
import com.housingplatform.purchase.dto.PurchaseAgreementResponse;
import com.housingplatform.purchase.dto.PurchasePreviewResponse;
import com.housingplatform.purchase.service.PropertyFinancingResolver.FinancingTerms;
import com.housingplatform.shared.domain.Currency;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Agreements between the buyer and the provider (Dream Teams Trading PLC by default) on a purchase
 * order. The first one, the Promise to Purchase, is signed inside order creation; later ones are
 * issued automatically when the order reaches the stage their template names.
 */
public interface PurchaseAgreementService {

  // ---- used by the order workflow (entity-level, same transaction)

  /** Renders the agreements a buyer must sign to create an order on this property. */
  List<PurchasePreviewResponse.AgreementPreview> previewOrderCreationAgreements(
      Property property, UUID buyerId, Currency currency, BigDecimal price, FinancingTerms terms);

  /**
   * Issues every active ORDER_CREATION agreement on the new order and applies the buyer's signature
   * to the Promise to Purchase. Fails if the referenced template is not the current one.
   */
  void signPromiseToPurchaseAtCreation(
      PropertyPurchaseOrder order, AgreementSignatureRequest signature, SignatureEvidence evidence);

  /** Issues the active agreements for a lifecycle trigger that are not yet on the order. */
  void issueForTrigger(PropertyPurchaseOrder order, AgreementTemplate.IssueTrigger trigger);

  /** Voids every agreement that is not fully signed (order closed). */
  void voidOpenAgreements(PropertyPurchaseOrder order, String reason);

  /** Voids agreements that only apply to financed orders (buyer converted to cash). */
  void voidFinancingAgreements(PropertyPurchaseOrder order, String reason);

  boolean hasUnsignedBlockingAgreements(PropertyPurchaseOrder order);

  // ---- buyer / viewer endpoints

  List<PurchaseAgreementResponse> list(PurchaseOrderActor actor, UUID orderId);

  PurchaseAgreementResponse get(PurchaseOrderActor actor, UUID orderId, UUID agreementId);

  PurchaseAgreementResponse sign(
      PurchaseOrderActor buyer,
      UUID orderId,
      UUID agreementId,
      AgreementSignatureRequest signature,
      SignatureEvidence evidence);

  // ---- admin

  PurchaseAgreementResponse countersign(UUID adminUserId, UUID agreementId);

  PurchaseAgreementResponse issueManually(UUID adminUserId, UUID orderId, UUID templateId);

  List<AgreementTemplateResponse> listTemplates();

  AgreementTemplateResponse createTemplateVersion(AgreementTemplateRequest request);

  AgreementTemplateResponse setTemplateActive(UUID templateId, boolean active);
}
