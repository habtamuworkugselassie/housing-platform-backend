package com.housingplatform.purchase.service.impl;

import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.property.domain.Property;
import com.housingplatform.property.repository.PropertyRepository;
import com.housingplatform.purchase.config.PurchaseProviderProperties;
import com.housingplatform.purchase.domain.AgreementTemplate;
import com.housingplatform.purchase.domain.AgreementTemplate.AgreementType;
import com.housingplatform.purchase.domain.AgreementTemplate.AppliesTo;
import com.housingplatform.purchase.domain.AgreementTemplate.IssueTrigger;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder.PurchaseType;
import com.housingplatform.purchase.domain.PurchaseAgreement;
import com.housingplatform.purchase.domain.PurchaseAgreement.AgreementStatus;
import com.housingplatform.purchase.domain.PurchaseAgreement.SignatureMethod;
import com.housingplatform.purchase.domain.PurchaseOrderFinancing;
import com.housingplatform.purchase.dto.AgreementSignatureRequest;
import com.housingplatform.purchase.dto.AgreementTemplateRequest;
import com.housingplatform.purchase.dto.AgreementTemplateResponse;
import com.housingplatform.purchase.dto.PurchaseAgreementResponse;
import com.housingplatform.purchase.dto.PurchasePreviewResponse;
import com.housingplatform.purchase.repository.AgreementTemplateRepository;
import com.housingplatform.purchase.repository.PropertyPurchaseOrderRepository;
import com.housingplatform.purchase.repository.PurchaseAgreementRepository;
import com.housingplatform.purchase.service.AgreementTemplateRenderer;
import com.housingplatform.purchase.service.PropertyFinancingResolver.FinancingTerms;
import com.housingplatform.purchase.service.PurchaseAgreementMapper;
import com.housingplatform.purchase.service.PurchaseAgreementService;
import com.housingplatform.purchase.service.PurchaseOrderAccess;
import com.housingplatform.purchase.service.PurchaseOrderActor;
import com.housingplatform.purchase.service.PurchaseOrderEvents.PurchaseAgreementIssuedEvent;
import com.housingplatform.purchase.service.SignatureEvidence;
import com.housingplatform.shared.domain.Currency;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PurchaseAgreementServiceImpl implements PurchaseAgreementService {

  private static final String ORDER_NUMBER_PENDING = "to be assigned";

  private final AgreementTemplateRepository templateRepository;
  private final PurchaseAgreementRepository agreementRepository;
  private final PropertyPurchaseOrderRepository orderRepository;
  private final PropertyRepository propertyRepository;
  private final UserRepository userRepository;
  private final OrganizationRepository organizationRepository;
  private final PurchaseProviderProperties provider;
  private final PurchaseAgreementMapper mapper;
  private final ApplicationEventPublisher eventPublisher;

  // ------------------------------------------------------------------ workflow hooks

  @Override
  @Transactional(readOnly = true)
  public List<PurchasePreviewResponse.AgreementPreview> previewOrderCreationAgreements(
      Property property, UUID buyerId, Currency currency, BigDecimal price, FinancingTerms terms) {
    PurchaseType type = terms != null ? PurchaseType.BANK_FINANCED : PurchaseType.CASH;
    Map<String, String> values = new HashMap<>();
    putProvider(values);
    values.put("order.number", ORDER_NUMBER_PENDING);
    values.put("date.today", LocalDate.now().toString());
    putBuyer(values, buyerId, null, null);
    putProperty(values, property);
    values.put("seller.companyName", organizationName(property.getRealEstateCompanyId()));
    values.put("price.amount", money(price));
    values.put("price.currency", currency.getCode());
    if (terms != null) {
      values.put("financing", "true");
      values.put("financing.financedAmount", money(terms.financedAmount()));
      values.put("financing.cashPortion", money(terms.cashPortion()));
      values.put("financing.bankName", organizationName(terms.eligible().offer().getBankId()));
      values.put("financing.interestRate", terms.eligible().interestRate().toPlainString());
      values.put("financing.tenureMonths", String.valueOf(terms.tenureMonths()));
    }
    List<PurchasePreviewResponse.AgreementPreview> previews = new ArrayList<>();
    for (AgreementTemplate t : activeTemplates(IssueTrigger.ORDER_CREATION, type)) {
      previews.add(
          PurchasePreviewResponse.AgreementPreview.builder()
              .templateId(t.getId())
              .type(t.getType())
              .version(t.getTemplateVersion())
              .title(t.getTitle())
              .content(AgreementTemplateRenderer.render(t.getBody(), values))
              .build());
    }
    return previews;
  }

  @Override
  public void signPromiseToPurchaseAtCreation(
      PropertyPurchaseOrder order,
      AgreementSignatureRequest signature,
      SignatureEvidence evidence) {
    AgreementTemplate promise =
        templateRepository
            .findFirstByTypeAndActiveTrueOrderByTemplateVersionDesc(
                AgreementType.PROMISE_TO_PURCHASE)
            .orElseThrow(
                () ->
                    new BusinessException(
                        "No active Promise to Purchase agreement is configured; orders cannot be"
                            + " created"));
    if (!promise.getId().equals(signature.getTemplateId())) {
      throw new BusinessException(
          "The Promise to Purchase agreement has changed (current version "
              + promise.getTemplateVersion()
              + "). Please review and sign the current text");
    }
    PurchaseAgreement signed = null;
    for (AgreementTemplate t :
        activeTemplates(IssueTrigger.ORDER_CREATION, order.getPurchaseType())) {
      PurchaseAgreement a = issue(order, t);
      if (t.getId().equals(promise.getId())) {
        signed = a;
      }
    }
    if (signed == null) {
      signed = issue(order, promise);
    }
    applyBuyerSignature(signed, signature, evidence);
  }

  @Override
  public void issueForTrigger(PropertyPurchaseOrder order, IssueTrigger trigger) {
    for (AgreementTemplate t : activeTemplates(trigger, order.getPurchaseType())) {
      boolean alreadyIssued =
          order.getAgreements().stream()
              .anyMatch(
                  a ->
                      a.getTemplateId().equals(t.getId()) && a.getStatus() != AgreementStatus.VOID);
      if (!alreadyIssued) {
        PurchaseAgreement a = issue(order, t);
        eventPublisher.publishEvent(new PurchaseAgreementIssuedEvent(order.getId(), a.getId()));
      }
    }
  }

  @Override
  public void voidOpenAgreements(PropertyPurchaseOrder order, String reason) {
    for (PurchaseAgreement a : order.getAgreements()) {
      if (!a.isFullySigned() && a.getStatus() != AgreementStatus.VOID) {
        voidAgreement(a, reason);
      }
    }
  }

  @Override
  public void voidFinancingAgreements(PropertyPurchaseOrder order, String reason) {
    for (PurchaseAgreement a : order.getAgreements()) {
      if (a.getStatus() == AgreementStatus.VOID) {
        continue;
      }
      boolean financingOnly =
          templateRepository
              .findById(a.getTemplateId())
              .map(t -> t.getAppliesTo() == AppliesTo.FINANCED_ONLY)
              .orElse(false);
      if (financingOnly) {
        voidAgreement(a, reason);
      }
    }
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasUnsignedBlockingAgreements(PropertyPurchaseOrder order) {
    return order.getAgreements().stream()
        .anyMatch(
            a ->
                Boolean.TRUE.equals(a.getBlocksCompletion())
                    && a.getStatus() != AgreementStatus.VOID
                    && !a.isFullySigned());
  }

  // ------------------------------------------------------------------ endpoints

  @Override
  @Transactional(readOnly = true)
  public List<PurchaseAgreementResponse> list(PurchaseOrderActor actor, UUID orderId) {
    PropertyPurchaseOrder order = loadViewableOrder(actor, orderId);
    return order.getAgreements().stream().map(a -> mapper.toResponse(a, false)).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public PurchaseAgreementResponse get(PurchaseOrderActor actor, UUID orderId, UUID agreementId) {
    loadViewableOrder(actor, orderId);
    return mapper.toResponse(loadAgreement(orderId, agreementId), true);
  }

  @Override
  public PurchaseAgreementResponse sign(
      PurchaseOrderActor buyer,
      UUID orderId,
      UUID agreementId,
      AgreementSignatureRequest signature,
      SignatureEvidence evidence) {
    PropertyPurchaseOrder order = loadViewableOrder(buyer, orderId);
    if (!PurchaseOrderAccess.isBuyer(buyer, order)) {
      throw new BusinessException("Only the buyer can sign this agreement");
    }
    PurchaseAgreement a = loadAgreement(orderId, agreementId);
    if (a.getStatus() != AgreementStatus.PENDING_BUYER_SIGNATURE) {
      throw new BusinessException(
          "Agreement '"
              + a.getTitle()
              + "' is not awaiting the buyer's signature ("
              + a.getStatus()
              + ")");
    }
    if (!a.getTemplateId().equals(signature.getTemplateId())) {
      throw new BusinessException("The signature does not reference the agreement's template");
    }
    applyBuyerSignature(a, signature, evidence);
    return mapper.toResponse(agreementRepository.save(a), true);
  }

  @Override
  public PurchaseAgreementResponse countersign(UUID adminUserId, UUID agreementId) {
    PurchaseAgreement a =
        agreementRepository
            .findById(agreementId)
            .orElseThrow(() -> new ResourceNotFoundException("PurchaseAgreement", agreementId));
    if (a.getStatus() != AgreementStatus.PENDING_PROVIDER_SIGNATURE) {
      throw new BusinessException("Agreement is not awaiting the provider's signature");
    }
    applyProviderSignature(a, adminUserId);
    return mapper.toResponse(agreementRepository.save(a), true);
  }

  @Override
  public PurchaseAgreementResponse issueManually(UUID adminUserId, UUID orderId, UUID templateId) {
    PropertyPurchaseOrder order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", orderId));
    if (order.getStatus().isTerminal()) {
      throw new BusinessException("Cannot issue an agreement on a closed order");
    }
    AgreementTemplate t =
        templateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("AgreementTemplate", templateId));
    if (!Boolean.TRUE.equals(t.getActive())) {
      throw new BusinessException("Only active templates can be issued");
    }
    PurchaseAgreement a = issue(order, t);
    orderRepository.save(order);
    eventPublisher.publishEvent(new PurchaseAgreementIssuedEvent(order.getId(), a.getId()));
    return mapper.toResponse(a, true);
  }

  @Override
  @Transactional(readOnly = true)
  public List<AgreementTemplateResponse> listTemplates() {
    return templateRepository.findAllByOrderByTypeAscTemplateVersionDesc().stream()
        .map(mapper::toResponse)
        .toList();
  }

  @Override
  public AgreementTemplateResponse createTemplateVersion(AgreementTemplateRequest request) {
    int nextVersion =
        templateRepository
            .findFirstByTypeOrderByTemplateVersionDesc(request.getType())
            .map(t -> t.getTemplateVersion() + 1)
            .orElse(1);
    AgreementTemplate t =
        AgreementTemplate.builder()
            .type(request.getType())
            .templateVersion(nextVersion)
            .title(request.getTitle().trim())
            .body(request.getBody())
            .issueTrigger(request.getIssueTrigger())
            .appliesTo(request.getAppliesTo() != null ? request.getAppliesTo() : AppliesTo.ALL)
            .sequence(request.getSequence() != null ? request.getSequence() : 0)
            .blocksCompletion(
                request.getBlocksCompletion() == null || request.getBlocksCompletion())
            .active(false)
            .build();
    t = templateRepository.save(t);
    if (Boolean.TRUE.equals(request.getActivate())) {
      activate(t);
    }
    return mapper.toResponse(t);
  }

  @Override
  public AgreementTemplateResponse setTemplateActive(UUID templateId, boolean active) {
    AgreementTemplate t =
        templateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("AgreementTemplate", templateId));
    if (active) {
      activate(t);
    } else {
      t.setActive(false);
      templateRepository.save(t);
    }
    return mapper.toResponse(t);
  }

  // ------------------------------------------------------------------ helpers

  /** Only one version of a type is active at a time. */
  private void activate(AgreementTemplate t) {
    templateRepository
        .findFirstByTypeAndActiveTrueOrderByTemplateVersionDesc(t.getType())
        .filter(current -> !current.getId().equals(t.getId()))
        .ifPresent(
            current -> {
              current.setActive(false);
              templateRepository.save(current);
            });
    t.setActive(true);
    templateRepository.save(t);
  }

  private List<AgreementTemplate> activeTemplates(IssueTrigger trigger, PurchaseType type) {
    List<AgreementTemplate> out = new ArrayList<>();
    for (AgreementTemplate t :
        templateRepository.findByActiveTrueAndIssueTriggerOrderBySequenceAscTemplateVersionDesc(
            trigger)) {
      boolean applies =
          switch (t.getAppliesTo()) {
            case ALL -> true;
            case CASH_ONLY -> type == PurchaseType.CASH;
            case FINANCED_ONLY -> type == PurchaseType.BANK_FINANCED;
          };
      if (applies) {
        out.add(t);
      }
    }
    return out;
  }

  private PurchaseAgreement issue(PropertyPurchaseOrder order, AgreementTemplate t) {
    String content = AgreementTemplateRenderer.render(t.getBody(), contextFor(order));
    PurchaseAgreement a =
        PurchaseAgreement.builder()
            .purchaseOrder(order)
            .templateId(t.getId())
            .type(t.getType())
            .templateVersion(t.getTemplateVersion())
            .sequence(t.getSequence())
            .title(t.getTitle())
            .content(content)
            .contentHash(AgreementTemplateRenderer.sha256Hex(content))
            .status(AgreementStatus.PENDING_BUYER_SIGNATURE)
            .blocksCompletion(t.getBlocksCompletion())
            .issuedAt(LocalDateTime.now())
            .buyerUserId(order.getBuyerId())
            .providerName(provider.getName())
            .build();
    order.getAgreements().add(a);
    return a;
  }

  private void applyBuyerSignature(
      PurchaseAgreement a, AgreementSignatureRequest signature, SignatureEvidence evidence) {
    if (!signature.isAccepted()) {
      throw new BusinessException("The agreement must be accepted to be signed");
    }
    a.setBuyerSignatoryName(signature.getSignatoryFullName().trim());
    a.setBuyerSignatureMethod(SignatureMethod.TYPED_NAME);
    a.setBuyerSignedAt(LocalDateTime.now());
    a.setBuyerSignatureIp(truncate(evidence != null ? evidence.ipAddress() : null, 64));
    a.setBuyerSignatureUserAgent(truncate(evidence != null ? evidence.userAgent() : null, 512));
    if (provider.isAutoCountersign()) {
      applyProviderSignature(a, null);
    } else {
      a.setStatus(AgreementStatus.PENDING_PROVIDER_SIGNATURE);
    }
  }

  private void applyProviderSignature(PurchaseAgreement a, UUID adminUserId) {
    a.setProviderSignatoryName(provider.getSignatoryName());
    a.setProviderSignatoryTitle(provider.getSignatoryTitle());
    a.setProviderSignedAt(LocalDateTime.now());
    a.setProviderSignedByUserId(adminUserId);
    a.setStatus(AgreementStatus.FULLY_SIGNED);
  }

  private static void voidAgreement(PurchaseAgreement a, String reason) {
    a.setStatus(AgreementStatus.VOID);
    a.setVoidedAt(LocalDateTime.now());
    a.setVoidReason(reason);
  }

  private Map<String, String> contextFor(PropertyPurchaseOrder order) {
    Map<String, String> values = new HashMap<>();
    putProvider(values);
    values.put("order.number", order.getOrderNumber());
    values.put("date.today", LocalDate.now().toString());
    putBuyer(values, order.getBuyerId(), order.getContactPhone(), order.getContactEmail());
    propertyRepository.findById(order.getPropertyId()).ifPresent(p -> putProperty(values, p));
    values.put("seller.companyName", organizationName(order.getRealEstateCompanyId()));
    values.put("price.amount", money(order.getListedPrice()));
    values.put("price.currency", order.getCurrency().getCode());
    PurchaseOrderFinancing f = order.getFinancing();
    if (order.getPurchaseType() == PurchaseType.BANK_FINANCED && f != null) {
      values.put("financing", "true");
      values.put("financing.financedAmount", money(f.getFinancedAmount()));
      values.put("financing.cashPortion", money(f.getCashPortionAmount()));
      values.put("financing.bankName", organizationName(f.getBankId()));
      values.put("financing.interestRate", f.getAppliedInterestRate().toPlainString());
      values.put("financing.tenureMonths", String.valueOf(f.getTenureMonths()));
      values.put(
          "financing.approvedAmount",
          money(f.getApprovedAmount() != null ? f.getApprovedAmount() : f.getFinancedAmount()));
    }
    return values;
  }

  private void putProvider(Map<String, String> values) {
    values.put("provider.name", provider.getName());
    values.put("provider.registrationNumber", provider.getRegistrationNumber());
    values.put("provider.address", provider.getAddress());
    values.put("provider.email", provider.getEmail());
    values.put("provider.phone", provider.getPhone());
    values.put("provider.signatoryName", provider.getSignatoryName());
    values.put("provider.signatoryTitle", provider.getSignatoryTitle());
  }

  private void putBuyer(Map<String, String> values, UUID buyerId, String phone, String email) {
    userRepository
        .findById(buyerId)
        .ifPresentOrElse(
            u -> {
              String name =
                  ((u.getFirstName() != null ? u.getFirstName() : "")
                          + " "
                          + (u.getLastName() != null ? u.getLastName() : ""))
                      .trim();
              values.put("buyer.fullName", name.isEmpty() ? "the Buyer" : name);
              values.put("buyer.phone", phone != null ? phone : nz(u.getPhoneNumber()));
              values.put("buyer.email", email != null ? email : nz(u.getEmail()));
            },
            () -> {
              values.put("buyer.fullName", "the Buyer");
              values.put("buyer.phone", nz(phone));
              values.put("buyer.email", nz(email));
            });
  }

  private static void putProperty(Map<String, String> values, Property p) {
    values.put("property.title", nz(p.getTitle()));
    values.put("property.address", nz(p.getAddress()));
    values.put("property.city", nz(p.getCity()));
    values.put("property.unitNumber", nz(p.getUnitNumber()));
  }

  private String organizationName(UUID organizationId) {
    if (organizationId == null) {
      return "";
    }
    return organizationRepository.findById(organizationId).map(o -> o.getName()).orElse("");
  }

  private PropertyPurchaseOrder loadViewableOrder(PurchaseOrderActor actor, UUID orderId) {
    PropertyPurchaseOrder order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", orderId));
    if (!PurchaseOrderAccess.canView(actor, order)) {
      throw new ResourceNotFoundException("PurchaseOrder", orderId);
    }
    return order;
  }

  private PurchaseAgreement loadAgreement(UUID orderId, UUID agreementId) {
    return agreementRepository
        .findByIdAndPurchaseOrderId(agreementId, orderId)
        .orElseThrow(() -> new ResourceNotFoundException("PurchaseAgreement", agreementId));
  }

  static String money(BigDecimal value) {
    return value == null ? "" : String.format(Locale.US, "%,.2f", value);
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }

  private static String truncate(String s, int max) {
    if (s == null) {
      return null;
    }
    return s.length() <= max ? s : s.substring(0, max);
  }
}
