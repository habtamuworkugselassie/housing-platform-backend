package com.housingplatform.purchase.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.User;
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
import com.housingplatform.purchase.domain.PropertyPurchaseOrder.PurchaseOrderStatus;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder.PurchaseType;
import com.housingplatform.purchase.domain.PurchaseAgreement;
import com.housingplatform.purchase.domain.PurchaseAgreement.AgreementStatus;
import com.housingplatform.purchase.domain.PurchaseAgreement.SignatureMethod;
import com.housingplatform.purchase.domain.PurchaseOrderFinancing;
import com.housingplatform.purchase.domain.PurchaseOrderFinancing.FinancingMode;
import com.housingplatform.purchase.domain.PurchaseOrderFinancing.FinancingStatus;
import com.housingplatform.purchase.domain.PurchaseOrderFinancing.OfferLevel;
import com.housingplatform.purchase.dto.AgreementSignatureRequest;
import com.housingplatform.purchase.dto.AgreementTemplateRequest;
import com.housingplatform.purchase.dto.AgreementTemplateResponse;
import com.housingplatform.purchase.dto.PurchaseAgreementResponse;
import com.housingplatform.purchase.repository.AgreementTemplateRepository;
import com.housingplatform.purchase.repository.PropertyPurchaseOrderRepository;
import com.housingplatform.purchase.repository.PurchaseAgreementRepository;
import com.housingplatform.purchase.service.AgreementTemplateRenderer;
import com.housingplatform.purchase.service.PurchaseAgreementMapper;
import com.housingplatform.purchase.service.PurchaseOrderActor;
import com.housingplatform.purchase.service.PurchaseOrderEvents.PurchaseAgreementIssuedEvent;
import com.housingplatform.purchase.service.SignatureEvidence;
import com.housingplatform.shared.domain.Currency;
import com.housingplatform.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseAgreementServiceImplTest {

  @Mock private AgreementTemplateRepository templateRepository;
  @Mock private PurchaseAgreementRepository agreementRepository;
  @Mock private PropertyPurchaseOrderRepository orderRepository;
  @Mock private PropertyRepository propertyRepository;
  @Mock private UserRepository userRepository;
  @Mock private OrganizationRepository organizationRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Spy private PurchaseProviderProperties provider = new PurchaseProviderProperties();
  @Spy private PurchaseAgreementMapper mapper = new PurchaseAgreementMapper();

  private PurchaseAgreementServiceImpl service;

  private final UUID buyerId = UUID.randomUUID();
  private final UUID companyId = UUID.randomUUID();
  private final UUID bankId = UUID.randomUUID();
  private AgreementTemplate promise;
  private final List<AgreementTemplate> templates = new ArrayList<>();
  private Property property;

  @BeforeEach
  void setUp() {
    service =
        new PurchaseAgreementServiceImpl(
            templateRepository,
            agreementRepository,
            orderRepository,
            propertyRepository,
            userRepository,
            organizationRepository,
            provider,
            mapper,
            eventPublisher);

    User buyer = new User();
    buyer.setId(buyerId);
    buyer.setFirstName("Abebe");
    buyer.setLastName("Kebede");
    buyer.setEmail("abebe@example.com");
    when(userRepository.findById(buyerId)).thenReturn(Optional.of(buyer));

    Organization company = new Organization();
    company.setId(companyId);
    company.setName("Ayat Real Estate");
    when(organizationRepository.findById(companyId)).thenReturn(Optional.of(company));
    Organization bank = new Organization();
    bank.setId(bankId);
    bank.setName("Awash Bank");
    when(organizationRepository.findById(bankId)).thenReturn(Optional.of(bank));

    property =
        Property.builder()
            .title("3BR Apartment, Bole")
            .address("Bole Road")
            .city("Addis Ababa")
            .unitNumber("A-101")
            .realEstateCompanyId(companyId)
            .build();
    property.setId(UUID.randomUUID());
    when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));

    promise =
        template(
            AgreementType.PROMISE_TO_PURCHASE,
            1,
            IssueTrigger.ORDER_CREATION,
            AppliesTo.ALL,
            true,
            "# PROMISE {{order.number}}\n{{buyer.fullName}} / {{provider.name}} / {{price.amount}} {{price.currency}}"
                + "{{#financing}} / loan {{financing.financedAmount}} from {{financing.bankName}}{{/financing}}"
                + "{{^financing}} / cash{{/financing}} / {{seller.companyName}} / {{property.title}}");

    when(templateRepository.findFirstByTypeAndActiveTrueOrderByTemplateVersionDesc(any()))
        .thenAnswer(
            inv ->
                templates.stream()
                    .filter(t -> t.getType() == inv.getArgument(0) && t.getActive())
                    .findFirst());
    when(templateRepository.findByActiveTrueAndIssueTriggerOrderBySequenceAscTemplateVersionDesc(
            any()))
        .thenAnswer(
            inv ->
                templates.stream()
                    .filter(t -> t.getIssueTrigger() == inv.getArgument(0) && t.getActive())
                    .toList());
    when(templateRepository.findById(any()))
        .thenAnswer(
            inv ->
                templates.stream().filter(t -> t.getId().equals(inv.getArgument(0))).findFirst());
    when(templateRepository.save(any()))
        .thenAnswer(
            inv -> {
              AgreementTemplate t = inv.getArgument(0);
              if (t.getId() == null) {
                t.setId(UUID.randomUUID());
                templates.add(t);
              }
              return t;
            });
    when(agreementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  private AgreementTemplate template(
      AgreementType type,
      int version,
      IssueTrigger trigger,
      AppliesTo appliesTo,
      boolean active,
      String body) {
    AgreementTemplate t =
        AgreementTemplate.builder()
            .type(type)
            .templateVersion(version)
            .title(type.name())
            .body(body)
            .issueTrigger(trigger)
            .appliesTo(appliesTo)
            .sequence(templates.size() + 1)
            .blocksCompletion(true)
            .active(active)
            .build();
    t.setId(UUID.randomUUID());
    templates.add(t);
    return t;
  }

  private PropertyPurchaseOrder order(boolean financed) {
    PropertyPurchaseOrder order =
        PropertyPurchaseOrder.builder()
            .orderNumber("PPO-2026-TEST0001")
            .propertyId(property.getId())
            .buyerId(buyerId)
            .realEstateCompanyId(companyId)
            .contactPhone("+251911223344")
            .contactEmail("abebe@example.com")
            .purchaseType(financed ? PurchaseType.BANK_FINANCED : PurchaseType.CASH)
            .status(PurchaseOrderStatus.PENDING_SELLER_REVIEW)
            .listedPrice(new BigDecimal("8500000.00"))
            .currency(Currency.ETB)
            .build();
    order.setId(UUID.randomUUID());
    if (financed) {
      order.setFinancing(
          PurchaseOrderFinancing.builder()
              .purchaseOrder(order)
              .financingOfferId(UUID.randomUUID())
              .bankId(bankId)
              .creditProductId(UUID.randomUUID())
              .offerLevel(OfferLevel.PROPERTY)
              .appliedInterestRate(new BigDecimal("14.50"))
              .appliedLtvRatio(new BigDecimal("0.80"))
              .minFinanceableAmount(new BigDecimal("500000.00"))
              .maxFinanceableAmount(new BigDecimal("6800000.00"))
              .financingMode(FinancingMode.PARTIAL)
              .financedAmount(new BigDecimal("4250000.00"))
              .cashPortionAmount(new BigDecimal("4250000.00"))
              .financingCoverageRatio(new BigDecimal("0.5000"))
              .tenureMonths(180)
              .financingStatus(FinancingStatus.APPLICATION_SUBMITTED)
              .build());
    }
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
    return order;
  }

  private AgreementSignatureRequest signature(UUID templateId) {
    AgreementSignatureRequest s = new AgreementSignatureRequest();
    s.setTemplateId(templateId);
    s.setAccepted(true);
    s.setSignatoryFullName("  Abebe Kebede ");
    return s;
  }

  // ------------------------------------------------------------------ promise to purchase

  @Test
  void signsThePromiseWithRenderedContentHashAndBothSignatures() {
    PropertyPurchaseOrder order = order(true);
    service.signPromiseToPurchaseAtCreation(
        order, signature(promise.getId()), new SignatureEvidence("41.0.0.1", "Mozilla"));

    assertThat(order.getAgreements()).hasSize(1);
    PurchaseAgreement a = order.getAgreements().get(0);
    assertThat(a.getType()).isEqualTo(AgreementType.PROMISE_TO_PURCHASE);
    assertThat(a.getTemplateVersion()).isEqualTo(1);
    assertThat(a.getContent())
        .contains("PROMISE PPO-2026-TEST0001")
        .contains("Abebe Kebede / Dream Team PLC / 8,500,000.00 ETB")
        .contains("loan 4,250,000.00 from Awash Bank")
        .contains("Ayat Real Estate / 3BR Apartment, Bole")
        .doesNotContain("cash");
    assertThat(a.getContentHash()).isEqualTo(AgreementTemplateRenderer.sha256Hex(a.getContent()));
    assertThat(a.getStatus()).isEqualTo(AgreementStatus.FULLY_SIGNED);
    assertThat(a.getBuyerSignatoryName()).isEqualTo("Abebe Kebede");
    assertThat(a.getBuyerSignatureMethod()).isEqualTo(SignatureMethod.TYPED_NAME);
    assertThat(a.getBuyerSignatureIp()).isEqualTo("41.0.0.1");
    assertThat(a.getBuyerSignatureUserAgent()).isEqualTo("Mozilla");
    assertThat(a.getBuyerSignedAt()).isNotNull();
    assertThat(a.getProviderName()).isEqualTo("Dream Team PLC");
    assertThat(a.getProviderSignatoryName()).isEqualTo("Authorized Signatory");
    assertThat(a.getProviderSignedAt()).isNotNull();
    assertThat(a.getProviderSignedByUserId()).isNull();
  }

  @Test
  void cashOrdersRenderTheCashBranch() {
    PropertyPurchaseOrder order = order(false);
    service.signPromiseToPurchaseAtCreation(
        order, signature(promise.getId()), SignatureEvidence.none());
    assertThat(order.getAgreements().get(0).getContent()).contains("/ cash").doesNotContain("loan");
  }

  @Test
  void rejectsASignatureOnAnOutdatedTemplateVersion() {
    AgreementTemplate v2 =
        template(
            AgreementType.PROMISE_TO_PURCHASE,
            2,
            IssueTrigger.ORDER_CREATION,
            AppliesTo.ALL,
            true,
            "v2");
    promise.setActive(false);
    PropertyPurchaseOrder order = order(false);
    assertThatThrownBy(
            () ->
                service.signPromiseToPurchaseAtCreation(
                    order, signature(promise.getId()), SignatureEvidence.none()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("current version 2");
    service.signPromiseToPurchaseAtCreation(order, signature(v2.getId()), SignatureEvidence.none());
    assertThat(order.getAgreements())
        .singleElement()
        .extracting(PurchaseAgreement::getTemplateVersion)
        .isEqualTo(2);
  }

  @Test
  void refusesToCreateOrdersWhenNoPromiseTemplateIsActive() {
    promise.setActive(false);
    assertThatThrownBy(
            () ->
                service.signPromiseToPurchaseAtCreation(
                    order(false), signature(promise.getId()), SignatureEvidence.none()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("No active Promise to Purchase");
  }

  @Test
  void withoutAutoCountersignThePromiseWaitsForTheProvider() {
    provider.setAutoCountersign(false);
    PropertyPurchaseOrder order = order(false);
    service.signPromiseToPurchaseAtCreation(
        order, signature(promise.getId()), SignatureEvidence.none());
    PurchaseAgreement a = order.getAgreements().get(0);
    assertThat(a.getStatus()).isEqualTo(AgreementStatus.PENDING_PROVIDER_SIGNATURE);
    assertThat(a.getProviderSignedAt()).isNull();
    when(agreementRepository.findById(a.getId())).thenReturn(Optional.of(a));

    UUID admin = UUID.randomUUID();
    a.setId(UUID.randomUUID());
    when(agreementRepository.findById(a.getId())).thenReturn(Optional.of(a));
    PurchaseAgreementResponse r = service.countersign(admin, a.getId());
    assertThat(r.getStatus()).isEqualTo(AgreementStatus.FULLY_SIGNED);
    assertThat(a.getProviderSignedByUserId()).isEqualTo(admin);
  }

  // ------------------------------------------------------------------ follow-ups

  @Test
  void followUpAgreementsAreIssuedOncePerTriggerAndFilteredByPurchaseType() {
    template(
        AgreementType.SALE_AGREEMENT,
        1,
        IssueTrigger.SELLER_ACCEPTANCE,
        AppliesTo.ALL,
        true,
        "sale {{order.number}}");
    template(
        AgreementType.FINANCING_ACKNOWLEDGEMENT,
        1,
        IssueTrigger.FINANCING_APPROVAL,
        AppliesTo.FINANCED_ONLY,
        true,
        "fin");
    template(
        AgreementType.OTHER, 1, IssueTrigger.SELLER_ACCEPTANCE, AppliesTo.ALL, false, "inactive");

    PropertyPurchaseOrder cash = order(false);
    service.issueForTrigger(cash, IssueTrigger.SELLER_ACCEPTANCE);
    service.issueForTrigger(cash, IssueTrigger.SELLER_ACCEPTANCE);
    service.issueForTrigger(cash, IssueTrigger.FINANCING_APPROVAL);
    assertThat(cash.getAgreements()).hasSize(1);
    assertThat(cash.getAgreements().get(0).getType()).isEqualTo(AgreementType.SALE_AGREEMENT);
    assertThat(cash.getAgreements().get(0).getStatus())
        .isEqualTo(AgreementStatus.PENDING_BUYER_SIGNATURE);
    verify(eventPublisher).publishEvent(any(PurchaseAgreementIssuedEvent.class));

    PropertyPurchaseOrder financed = order(true);
    service.issueForTrigger(financed, IssueTrigger.FINANCING_APPROVAL);
    assertThat(financed.getAgreements())
        .singleElement()
        .extracting(PurchaseAgreement::getType)
        .isEqualTo(AgreementType.FINANCING_ACKNOWLEDGEMENT);
  }

  @Test
  void buyerSignsAFollowUpAndBlockingLogicFollows() {
    template(
        AgreementType.SALE_AGREEMENT,
        1,
        IssueTrigger.SELLER_ACCEPTANCE,
        AppliesTo.ALL,
        true,
        "sale");
    PropertyPurchaseOrder order = order(false);
    service.issueForTrigger(order, IssueTrigger.SELLER_ACCEPTANCE);
    PurchaseAgreement a = order.getAgreements().get(0);
    a.setId(UUID.randomUUID());
    when(agreementRepository.findByIdAndPurchaseOrderId(a.getId(), order.getId()))
        .thenReturn(Optional.of(a));
    assertThat(service.hasUnsignedBlockingAgreements(order)).isTrue();

    PurchaseOrderActor stranger = PurchaseOrderActor.buyer(UUID.randomUUID());
    assertThatThrownBy(
            () ->
                service.sign(
                    stranger,
                    order.getId(),
                    a.getId(),
                    signature(a.getTemplateId()),
                    SignatureEvidence.none()))
        .isInstanceOf(com.housingplatform.shared.exception.ResourceNotFoundException.class);

    PurchaseOrderActor buyer = PurchaseOrderActor.buyer(buyerId);
    AgreementSignatureRequest wrongTemplate = signature(UUID.randomUUID());
    assertThatThrownBy(
            () ->
                service.sign(
                    buyer, order.getId(), a.getId(), wrongTemplate, SignatureEvidence.none()))
        .isInstanceOf(BusinessException.class);

    PurchaseAgreementResponse signed =
        service.sign(
            buyer,
            order.getId(),
            a.getId(),
            signature(a.getTemplateId()),
            SignatureEvidence.none());
    assertThat(signed.getStatus()).isEqualTo(AgreementStatus.FULLY_SIGNED);
    assertThat(signed.getContent()).isEqualTo("sale\n");
    assertThat(service.hasUnsignedBlockingAgreements(order)).isFalse();

    assertThatThrownBy(
            () ->
                service.sign(
                    buyer,
                    order.getId(),
                    a.getId(),
                    signature(a.getTemplateId()),
                    SignatureEvidence.none()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("not awaiting");
  }

  @Test
  void voidingKeepsSignedAgreementsAndDropsFinancingOnlyOnesOnConversion() {
    template(
        AgreementType.SALE_AGREEMENT,
        1,
        IssueTrigger.SELLER_ACCEPTANCE,
        AppliesTo.ALL,
        true,
        "sale");
    template(
        AgreementType.FINANCING_ACKNOWLEDGEMENT,
        1,
        IssueTrigger.FINANCING_APPROVAL,
        AppliesTo.FINANCED_ONLY,
        true,
        "fin");
    PropertyPurchaseOrder order = order(true);
    service.signPromiseToPurchaseAtCreation(
        order, signature(promise.getId()), SignatureEvidence.none());
    service.issueForTrigger(order, IssueTrigger.SELLER_ACCEPTANCE);
    service.issueForTrigger(order, IssueTrigger.FINANCING_APPROVAL);
    assertThat(order.getAgreements()).hasSize(3);

    service.voidFinancingAgreements(order, "converted");
    assertThat(order.getAgreements())
        .filteredOn(a -> a.getType() == AgreementType.FINANCING_ACKNOWLEDGEMENT)
        .singleElement()
        .extracting(PurchaseAgreement::getStatus)
        .isEqualTo(AgreementStatus.VOID);
    assertThat(order.getAgreements())
        .filteredOn(a -> a.getType() == AgreementType.SALE_AGREEMENT)
        .singleElement()
        .extracting(PurchaseAgreement::getStatus)
        .isEqualTo(AgreementStatus.PENDING_BUYER_SIGNATURE);

    service.voidOpenAgreements(order, "cancelled");
    assertThat(order.getAgreements())
        .filteredOn(a -> a.getType() == AgreementType.PROMISE_TO_PURCHASE)
        .singleElement()
        .extracting(PurchaseAgreement::getStatus)
        .isEqualTo(AgreementStatus.FULLY_SIGNED);
    assertThat(order.getAgreements())
        .filteredOn(a -> a.getType() == AgreementType.SALE_AGREEMENT)
        .singleElement()
        .extracting(PurchaseAgreement::getStatus)
        .isEqualTo(AgreementStatus.VOID);
    assertThat(service.hasUnsignedBlockingAgreements(order)).isFalse();
  }

  // ------------------------------------------------------------------ templates

  @Test
  void newTemplateVersionsAreNumberedAndActivationIsExclusive() {
    when(templateRepository.findFirstByTypeOrderByTemplateVersionDesc(
            AgreementType.PROMISE_TO_PURCHASE))
        .thenAnswer(
            inv ->
                templates.stream()
                    .filter(t -> t.getType() == AgreementType.PROMISE_TO_PURCHASE)
                    .max(java.util.Comparator.comparing(AgreementTemplate::getTemplateVersion)));
    AgreementTemplateRequest req = new AgreementTemplateRequest();
    req.setType(AgreementType.PROMISE_TO_PURCHASE);
    req.setTitle("Promise v2");
    req.setBody("new text");
    req.setIssueTrigger(IssueTrigger.ORDER_CREATION);
    req.setActivate(true);

    AgreementTemplateResponse created = service.createTemplateVersion(req);
    assertThat(created.getVersion()).isEqualTo(2);
    assertThat(created.getActive()).isTrue();
    assertThat(promise.getActive()).isFalse();

    service.setTemplateActive(promise.getId(), true);
    assertThat(promise.getActive()).isTrue();
    assertThat(
            templates.stream()
                .filter(t -> t.getTemplateVersion() == 2)
                .findFirst()
                .get()
                .getActive())
        .isFalse();
  }

  @Test
  void previewRendersWithoutAnOrderNumber() {
    List<com.housingplatform.purchase.dto.PurchasePreviewResponse.AgreementPreview> previews =
        service.previewOrderCreationAgreements(
            property, buyerId, Currency.ETB, new BigDecimal("8500000.00"), null);
    assertThat(previews).hasSize(1);
    assertThat(previews.get(0).getTemplateId()).isEqualTo(promise.getId());
    assertThat(previews.get(0).getContent()).contains("PROMISE to be assigned").contains("/ cash");
  }
}
