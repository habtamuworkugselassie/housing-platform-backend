package com.housingplatform.purchase.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housingplatform.banking.domain.CreditProduct;
import com.housingplatform.banking.domain.FinancingOffer;
import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.loan.domain.LoanApplication;
import com.housingplatform.loan.dto.LoanApplicationRequest;
import com.housingplatform.loan.dto.LoanApplicationResponse;
import com.housingplatform.loan.service.LoanApplicationService;
import com.housingplatform.property.domain.Property;
import com.housingplatform.property.repository.PropertyRepository;
import com.housingplatform.purchase.domain.AgreementTemplate.IssueTrigger;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder.PurchaseOrderStatus;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder.PurchaseType;
import com.housingplatform.purchase.domain.PurchaseOrderFinancing;
import com.housingplatform.purchase.domain.PurchaseOrderFinancing.FinancingMode;
import com.housingplatform.purchase.domain.PurchaseOrderFinancing.FinancingStatus;
import com.housingplatform.purchase.domain.PurchaseOrderFinancing.OfferLevel;
import com.housingplatform.purchase.dto.AgreementSignatureRequest;
import com.housingplatform.purchase.dto.CreatePurchaseOrderRequest;
import com.housingplatform.purchase.dto.UpdatePurchaseFinancingRequest;
import com.housingplatform.purchase.repository.PropertyPurchaseOrderRepository;
import com.housingplatform.purchase.service.PropertyFinancingResolver;
import com.housingplatform.purchase.service.PropertyFinancingResolver.EligibleOffer;
import com.housingplatform.purchase.service.PropertyFinancingResolver.FinancingResolution;
import com.housingplatform.purchase.service.PropertyFinancingResolver.FinancingTerms;
import com.housingplatform.purchase.service.PropertyFinancingResolver.NotAppliedReason;
import com.housingplatform.purchase.service.PurchaseAgreementService;
import com.housingplatform.purchase.service.PurchaseOrderActor;
import com.housingplatform.purchase.service.PurchaseOrderEvents.PurchaseOrderCreatedEvent;
import com.housingplatform.purchase.service.PurchaseOrderMapper;
import com.housingplatform.purchase.service.SignatureEvidence;
import com.housingplatform.shared.domain.Currency;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.DuplicateResourceException;
import com.housingplatform.shared.exception.ForbiddenOperationException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseOrderServiceImplTest {

  private static final BigDecimal PRICE = new BigDecimal("8500000.00");

  @Mock private PropertyPurchaseOrderRepository orderRepository;
  @Mock private PropertyRepository propertyRepository;
  @Mock private UserRepository userRepository;
  @Mock private PropertyFinancingResolver financingResolver;
  @Mock private LoanApplicationService loanApplicationService;
  @Mock private PurchaseOrderMapper mapper;
  @Mock private PurchaseAgreementService agreementService;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private CacheManager cacheManager;
  @Mock private Cache propertyCache;
  @InjectMocks private PurchaseOrderServiceImpl service;

  private Property property;
  private final UUID buyerId = UUID.randomUUID();
  private final UUID companyId = UUID.randomUUID();
  private final UUID agentId = UUID.randomUUID();
  private final PurchaseOrderActor buyer = PurchaseOrderActor.buyer(buyerId);
  private final PurchaseOrderActor seller =
      new PurchaseOrderActor(UUID.randomUUID(), companyId, agentId, false);

  @BeforeEach
  void setUp() {
    property =
        Property.builder()
            .title("3BR Apartment, Bole")
            .type(Property.PropertyType.APARTMENT)
            .status(Property.PropertyStatus.AVAILABLE)
            .verificationStatus(Property.VerificationStatus.VERIFIED)
            .category(Property.PropertyCategory.FOR_SALE)
            .constructionStatus(Property.ConstructionStatus.READY_TO_MOVE)
            .priceETB(PRICE)
            .realEstateCompanyId(companyId)
            .agentId(agentId)
            .build();
    property.setId(UUID.randomUUID());
    when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));
    when(propertyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(orderRepository.findByPropertyIdAndStatusIn(any(), any())).thenReturn(List.of());
    when(cacheManager.getCache("properties")).thenReturn(propertyCache);
    when(financingResolver.resolve(any(), any(), any(), any(), any()))
        .thenReturn(new FinancingResolution(null, NotAppliedReason.NONE_AVAILABLE));
  }

  // ------------------------------------------------------------------ fixtures

  private CreatePurchaseOrderRequest request() {
    CreatePurchaseOrderRequest r = new CreatePurchaseOrderRequest();
    r.setPropertyId(property.getId());
    r.setContactPhone("0911223344");
    AgreementSignatureRequest promise = new AgreementSignatureRequest();
    promise.setTemplateId(PROMISE_TEMPLATE_ID);
    promise.setAccepted(true);
    promise.setSignatoryFullName("Abebe Kebede");
    r.setPromiseToPurchase(promise);
    return r;
  }

  private static final UUID PROMISE_TEMPLATE_ID = UUID.randomUUID();
  private static final SignatureEvidence EVIDENCE = new SignatureEvidence("10.0.0.1", "JUnit");

  private FinancingTerms terms(String financed, FinancingMode mode) {
    CreditProduct product =
        CreditProduct.builder()
            .bankId(UUID.randomUUID())
            .name("Home Purchase Loan")
            .productType(CreditProduct.CreditProductType.HOME_PURCHASE)
            .interestRate(new BigDecimal("14.50"))
            .minTenureMonths(12)
            .maxTenureMonths(240)
            .maxLoanToValueRatio(new BigDecimal("0.80"))
            .minLoanAmount(new BigDecimal("500000"))
            .maxLoanAmount(new BigDecimal("20000000"))
            .status(CreditProduct.CreditProductStatus.ACTIVE)
            .build();
    product.setId(UUID.randomUUID());
    FinancingOffer offer =
        FinancingOffer.builder()
            .bankId(product.getBankId())
            .creditProductId(product.getId())
            .propertyId(property.getId())
            .status(FinancingOffer.FinancingOfferStatus.ACTIVE)
            .build();
    offer.setId(UUID.randomUUID());
    EligibleOffer eligible =
        new EligibleOffer(
            offer,
            product,
            OfferLevel.PROPERTY,
            new BigDecimal("14.50"),
            new BigDecimal("0.80"),
            new BigDecimal("500000.00"),
            new BigDecimal("6800000.00"));
    BigDecimal amount = new BigDecimal(financed);
    return new FinancingTerms(
        eligible,
        mode,
        amount,
        PRICE.subtract(amount),
        amount.divide(PRICE, 4, java.math.RoundingMode.HALF_UP),
        240,
        PropertyFinancingResolver.monthlyInstallment(amount, new BigDecimal("14.50"), 240));
  }

  private UUID stubLoanCreation() {
    UUID loanId = UUID.randomUUID();
    LoanApplicationResponse response = new LoanApplicationResponse();
    response.setId(loanId);
    when(loanApplicationService.createLoanApplication(eq(buyerId), any())).thenReturn(response);
    return loanId;
  }

  private PropertyPurchaseOrder savedOrder() {
    ArgumentCaptor<PropertyPurchaseOrder> captor =
        ArgumentCaptor.forClass(PropertyPurchaseOrder.class);
    verify(orderRepository).save(captor.capture());
    return captor.getValue();
  }

  private PropertyPurchaseOrder financedOrder(PurchaseOrderStatus status, String financed) {
    FinancingTerms t = terms(financed, FinancingMode.MAXIMUM);
    PropertyPurchaseOrder order =
        PropertyPurchaseOrder.builder()
            .orderNumber("PPO-2026-TEST0001")
            .propertyId(property.getId())
            .buyerId(buyerId)
            .realEstateCompanyId(companyId)
            .agentId(agentId)
            .contactPhone("+251911223344")
            .purchaseType(PurchaseType.BANK_FINANCED)
            .status(status)
            .listedPrice(PRICE)
            .currency(Currency.ETB)
            .expiresAt(LocalDateTime.now().plusDays(14))
            .build();
    order.setId(UUID.randomUUID());
    PurchaseOrderFinancing f =
        PurchaseOrderFinancing.builder()
            .purchaseOrder(order)
            .financingOfferId(t.eligible().offer().getId())
            .bankId(t.eligible().offer().getBankId())
            .creditProductId(t.eligible().product().getId())
            .offerLevel(OfferLevel.PROPERTY)
            .appliedInterestRate(t.eligible().interestRate())
            .appliedLtvRatio(t.eligible().ltvRatio())
            .minFinanceableAmount(t.eligible().minFinanceable())
            .maxFinanceableAmount(t.eligible().maxFinanceable())
            .financingMode(t.mode())
            .financedAmount(t.financedAmount())
            .cashPortionAmount(t.cashPortion())
            .financingCoverageRatio(t.coverageRatio())
            .tenureMonths(t.tenureMonths())
            .estimatedMonthlyInstallment(t.estimatedMonthlyInstallment())
            .loanApplicationId(UUID.randomUUID())
            .financingStatus(FinancingStatus.APPLICATION_SUBMITTED)
            .build();
    order.setFinancing(f);
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
    when(orderRepository.findByLoanApplicationId(f.getLoanApplicationId()))
        .thenReturn(Optional.of(order));
    return order;
  }

  // ------------------------------------------------------------------ creation

  @Test
  void createsCashOrderWhenNoFinancingProductIsLinked() {
    CreatePurchaseOrderRequest r = request();
    r.setContactEmail("  Buyer@Example.com ");
    r.setBuyerMessage("Can I view it on Saturday?");
    service.createPurchaseOrder(buyer, r, EVIDENCE);

    PropertyPurchaseOrder saved = savedOrder();
    assertThat(saved.getPurchaseType()).isEqualTo(PurchaseType.CASH);
    assertThat(saved.getFinancing()).isNull();
    assertThat(saved.getStatus()).isEqualTo(PurchaseOrderStatus.PENDING_SELLER_REVIEW);
    assertThat(saved.getContactPhone()).isEqualTo("+251911223344");
    assertThat(saved.getContactEmail()).isEqualTo("buyer@example.com");
    assertThat(saved.getListedPrice()).isEqualByComparingTo(PRICE);
    assertThat(saved.getCurrency()).isEqualTo(Currency.ETB);
    assertThat(saved.getRealEstateCompanyId()).isEqualTo(companyId);
    assertThat(saved.getAgentId()).isEqualTo(agentId);
    assertThat(saved.getOrderNumber()).startsWith("PPO-");
    assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(13));
    assertThat(saved.getStatusHistory()).hasSize(1);
    assertThat(saved.getStatusHistory().get(0).getToStatus())
        .isEqualTo(PurchaseOrderStatus.PENDING_SELLER_REVIEW);
    verify(loanApplicationService, never()).createLoanApplication(any(), any());
    verify(eventPublisher).publishEvent(any(PurchaseOrderCreatedEvent.class));
  }

  @Test
  void emailIsOptional() {
    service.createPurchaseOrder(buyer, request(), EVIDENCE);
    assertThat(savedOrder().getContactEmail()).isNull();
  }

  @Test
  void financingBlockOnCashOrderProducesAWarning() {
    CreatePurchaseOrderRequest r = request();
    r.setFinancing(new CreatePurchaseOrderRequest.FinancingSelection());
    r.getFinancing().setFinancedAmount(new BigDecimal("1000000"));
    service.createPurchaseOrder(buyer, r, EVIDENCE);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> warnings = ArgumentCaptor.forClass(List.class);
    verify(mapper).toResponse(any(), warnings.capture());
    assertThat(warnings.getValue())
        .singleElement()
        .asString()
        .contains("no active financing product");
  }

  @Test
  void createsFinancedOrderAndOpensLoanApplication() {
    FinancingTerms t = terms("4250000.00", FinancingMode.PARTIAL);
    when(financingResolver.resolve(any(), any(), any(), any(), any()))
        .thenReturn(new FinancingResolution(t, null));
    UUID loanId = stubLoanCreation();

    service.createPurchaseOrder(buyer, request(), EVIDENCE);

    PropertyPurchaseOrder saved = savedOrder();
    assertThat(saved.getPurchaseType()).isEqualTo(PurchaseType.BANK_FINANCED);
    PurchaseOrderFinancing f = saved.getFinancing();
    assertThat(f).isNotNull();
    assertThat(f.getLoanApplicationId()).isEqualTo(loanId);
    assertThat(f.getFinancingStatus()).isEqualTo(FinancingStatus.APPLICATION_SUBMITTED);
    assertThat(f.getFinancingMode()).isEqualTo(FinancingMode.PARTIAL);
    assertThat(f.getFinancedAmount()).isEqualByComparingTo("4250000.00");
    assertThat(f.getCashPortionAmount()).isEqualByComparingTo("4250000.00");
    assertThat(f.getFinancingCoverageRatio()).isEqualByComparingTo("0.5000");
    assertThat(f.getAppliedInterestRate()).isEqualByComparingTo("14.50");
    assertThat(f.getBankId()).isEqualTo(t.eligible().offer().getBankId());

    ArgumentCaptor<LoanApplicationRequest> loan =
        ArgumentCaptor.forClass(LoanApplicationRequest.class);
    verify(loanApplicationService).createLoanApplication(eq(buyerId), loan.capture());
    assertThat(loan.getValue().getRequestedAmount()).isEqualByComparingTo("4250000.00");
    assertThat(loan.getValue().getRequestedTenureMonths()).isEqualTo(240);
    assertThat(loan.getValue().getPropertyId()).isEqualTo(property.getId());
    assertThat(loan.getValue().getFinancingOfferId()).isEqualTo(t.eligible().offer().getId());
    assertThat(loan.getValue().getCurrency()).isEqualTo(Currency.ETB);
  }

  @Test
  void rejectsPropertiesThatAreNotPurchasable() {
    property.setStatus(Property.PropertyStatus.RESERVED);
    assertThatThrownBy(() -> service.createPurchaseOrder(buyer, request(), EVIDENCE))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("not available");

    property.setStatus(Property.PropertyStatus.AVAILABLE);
    property.setCategory(Property.PropertyCategory.FOR_RENTAL);
    assertThatThrownBy(() -> service.createPurchaseOrder(buyer, request(), EVIDENCE))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("not listed for sale");

    property.setCategory(Property.PropertyCategory.FOR_SALE);
    property.setVerificationStatus(Property.VerificationStatus.PENDING);
    assertThatThrownBy(() -> service.createPurchaseOrder(buyer, request(), EVIDENCE))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("not been verified");
  }

  @Test
  void rejectsSecondOpenOrderOnSameProperty() {
    when(orderRepository.existsByBuyerIdAndPropertyIdAndStatusIn(
            eq(buyerId), eq(property.getId()), any()))
        .thenReturn(true);
    assertThatThrownBy(() -> service.createPurchaseOrder(buyer, request(), EVIDENCE))
        .isInstanceOf(DuplicateResourceException.class);
  }

  @Test
  void sellerCannotOrderTheirOwnListing() {
    assertThatThrownBy(() -> service.createPurchaseOrder(seller, request(), EVIDENCE))
        .isInstanceOf(ForbiddenOperationException.class);
  }

  @Test
  void requiresAPriceInTheRequestedCurrency() {
    CreatePurchaseOrderRequest r = request();
    r.setCurrency(Currency.USD);
    assertThatThrownBy(() -> service.createPurchaseOrder(buyer, r, EVIDENCE))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("USD");
  }

  @Test
  void unknownPropertyIs404() {
    CreatePurchaseOrderRequest r = request();
    r.setPropertyId(UUID.randomUUID());
    assertThatThrownBy(() -> service.createPurchaseOrder(buyer, r, EVIDENCE))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  // ------------------------------------------------------------------ seller side

  @Test
  void acceptingAFinancedOrderWaitsForTheBankAndReservesTheProperty() {
    PropertyPurchaseOrder order =
        financedOrder(PurchaseOrderStatus.PENDING_SELLER_REVIEW, "6800000.00");
    service.accept(seller, order.getId(), "Welcome");

    assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.AWAITING_FINANCING);
    assertThat(order.getExpiresAt()).isNull();
    assertThat(property.getStatus()).isEqualTo(Property.PropertyStatus.RESERVED);
    verify(propertyCache).evict(property.getId());
  }

  @Test
  void acceptingACashOrderGoesStraightToPayment() {
    PropertyPurchaseOrder order =
        financedOrder(PurchaseOrderStatus.PENDING_SELLER_REVIEW, "6800000.00");
    order.setPurchaseType(PurchaseType.CASH);
    order.setFinancing(null);
    service.accept(seller, order.getId(), null);
    assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.AWAITING_PAYMENT);
  }

  @Test
  void onlyTheListingSellerCanAccept() {
    PropertyPurchaseOrder order =
        financedOrder(PurchaseOrderStatus.PENDING_SELLER_REVIEW, "6800000.00");
    PurchaseOrderActor otherSeller =
        new PurchaseOrderActor(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), false);
    assertThatThrownBy(() -> service.accept(otherSeller, order.getId(), null))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.accept(buyer, order.getId(), null))
        .isInstanceOf(ForbiddenOperationException.class);
  }

  @Test
  void acceptIsOnlyLegalFromPendingReview() {
    PropertyPurchaseOrder order =
        financedOrder(PurchaseOrderStatus.AWAITING_FINANCING, "6800000.00");
    assertThatThrownBy(() -> service.accept(seller, order.getId(), null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("PENDING_SELLER_REVIEW");
  }

  @Test
  void rejectingWithdrawsTheLoanApplication() {
    PropertyPurchaseOrder order =
        financedOrder(PurchaseOrderStatus.PENDING_SELLER_REVIEW, "6800000.00");
    service.reject(seller, order.getId(), "Already promised to someone else");

    assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.REJECTED);
    assertThat(order.getRejectionReason()).isEqualTo("Already promised to someone else");
    assertThat(order.getFinancing().getFinancingStatus()).isEqualTo(FinancingStatus.WITHDRAWN);
    verify(loanApplicationService)
        .withdrawLoanApplication(eq(order.getFinancing().getLoanApplicationId()), anyString());
  }

  @Test
  void completingMarksThePropertySoldAndRejectsCompetingOrders() {
    PropertyPurchaseOrder order = financedOrder(PurchaseOrderStatus.AWAITING_PAYMENT, "6800000.00");
    PropertyPurchaseOrder rival =
        financedOrder(PurchaseOrderStatus.PENDING_SELLER_REVIEW, "6800000.00");
    when(orderRepository.findByPropertyIdAndStatusIn(eq(property.getId()), any()))
        .thenReturn(List.of(order, rival));

    service.complete(seller, order.getId(), "CBE-TXN-42");

    assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.COMPLETED);
    assertThat(order.getPaymentReference()).isEqualTo("CBE-TXN-42");
    assertThat(property.getStatus()).isEqualTo(Property.PropertyStatus.SOLD);
    assertThat(rival.getStatus()).isEqualTo(PurchaseOrderStatus.REJECTED);
    assertThat(rival.getRejectionReason()).isEqualTo("PROPERTY_SOLD");
  }

  // ------------------------------------------------------------------ buyer side

  @Test
  void cancellingReleasesTheReservationAndWithdrawsTheLoan() {
    PropertyPurchaseOrder order =
        financedOrder(PurchaseOrderStatus.AWAITING_FINANCING, "6800000.00");
    property.setStatus(Property.PropertyStatus.RESERVED);
    when(orderRepository.findByPropertyIdAndStatusIn(eq(property.getId()), any()))
        .thenReturn(List.of(order));

    service.cancel(buyer, order.getId(), "Changed my mind");

    assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.CANCELLED);
    assertThat(order.getCancellationReason()).isEqualTo("Changed my mind");
    assertThat(property.getStatus()).isEqualTo(Property.PropertyStatus.AVAILABLE);
    verify(loanApplicationService).withdrawLoanApplication(any(), anyString());
  }

  @Test
  void strangersGet404NotForbidden() {
    PropertyPurchaseOrder order =
        financedOrder(PurchaseOrderStatus.PENDING_SELLER_REVIEW, "6800000.00");
    PurchaseOrderActor stranger = PurchaseOrderActor.buyer(UUID.randomUUID());
    assertThatThrownBy(() -> service.getPurchaseOrder(stranger, order.getId()))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.cancel(stranger, order.getId(), null))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void financingBankCanViewTheOrder() {
    PropertyPurchaseOrder order =
        financedOrder(PurchaseOrderStatus.AWAITING_FINANCING, "6800000.00");
    PurchaseOrderActor banker =
        new PurchaseOrderActor(UUID.randomUUID(), order.getFinancing().getBankId(), null, false);
    service.getPurchaseOrder(banker, order.getId());
    verify(mapper).toResponse(order);
  }

  @Test
  void updatingFinancingBeforeAcceptanceUpdatesTheLoanInPlace() {
    PropertyPurchaseOrder order =
        financedOrder(PurchaseOrderStatus.PENDING_SELLER_REVIEW, "6800000.00");
    FinancingTerms smaller = terms("4250000.00", FinancingMode.PARTIAL);
    when(financingResolver.recompute(any(), any(), any(), any(), any())).thenReturn(smaller);

    UpdatePurchaseFinancingRequest r = new UpdatePurchaseFinancingRequest();
    r.setFinancedAmount(new BigDecimal("4250000"));
    service.updateFinancing(buyer, order.getId(), r);

    assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.PENDING_SELLER_REVIEW);
    assertThat(order.getFinancing().getFinancedAmount()).isEqualByComparingTo("4250000.00");
    assertThat(order.getFinancing().getFinancingMode()).isEqualTo(FinancingMode.PARTIAL);
    verify(loanApplicationService)
        .updateRequestedTerms(
            eq(order.getFinancing().getLoanApplicationId()),
            eq(new BigDecimal("4250000.00")),
            eq(240));
    verify(loanApplicationService, never()).createLoanApplication(any(), any());
  }

  @Test
  void afterRejectionTheBuyerCanReapplyForLess() {
    PropertyPurchaseOrder order =
        financedOrder(PurchaseOrderStatus.FINANCING_REJECTED, "6800000.00");
    order.getFinancing().setFinancingStatus(FinancingStatus.REJECTED);
    UUID oldLoan = order.getFinancing().getLoanApplicationId();
    when(financingResolver.recompute(any(), any(), any(), any(), any()))
        .thenReturn(terms("3000000.00", FinancingMode.PARTIAL));
    UUID newLoan = stubLoanCreation();

    UpdatePurchaseFinancingRequest r = new UpdatePurchaseFinancingRequest();
    r.setFinancedAmount(new BigDecimal("3000000"));
    service.updateFinancing(buyer, order.getId(), r);

    assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.AWAITING_FINANCING);
    assertThat(order.getFinancing().getLoanApplicationId())
        .isEqualTo(newLoan)
        .isNotEqualTo(oldLoan);
    assertThat(order.getFinancing().getFinancingStatus())
        .isEqualTo(FinancingStatus.APPLICATION_SUBMITTED);
  }

  @Test
  void afterRejectionReapplyingForTheSameOrMoreIsRefused() {
    PropertyPurchaseOrder order =
        financedOrder(PurchaseOrderStatus.FINANCING_REJECTED, "6800000.00");
    when(financingResolver.recompute(any(), any(), any(), any(), any()))
        .thenReturn(terms("6800000.00", FinancingMode.MAXIMUM));
    UpdatePurchaseFinancingRequest r = new UpdatePurchaseFinancingRequest();
    assertThatThrownBy(() -> service.updateFinancing(buyer, order.getId(), r))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("smaller amount");
  }

  @Test
  void financingCannotBeChangedOnceTheBankIsReviewing() {
    PropertyPurchaseOrder order =
        financedOrder(PurchaseOrderStatus.AWAITING_FINANCING, "6800000.00");
    assertThatThrownBy(
            () ->
                service.updateFinancing(buyer, order.getId(), new UpdatePurchaseFinancingRequest()))
        .isInstanceOf(BusinessException.class);
  }

  // ------------------------------------------------------------------ bank decisions

  private LoanApplicationResponse approvedLoan(PropertyPurchaseOrder order, String amount) {
    LoanApplicationResponse loan = new LoanApplicationResponse();
    loan.setId(order.getFinancing().getLoanApplicationId());
    loan.setApprovedAmount(new BigDecimal(amount));
    loan.setApprovedInterestRate(new BigDecimal("14.00"));
    loan.setApprovedTenureMonths(180);
    when(loanApplicationService.getLoanApplicationById(loan.getId())).thenReturn(loan);
    return loan;
  }

  @Test
  void fullApprovalMovesTheOrderToPayment() {
    PropertyPurchaseOrder order =
        financedOrder(PurchaseOrderStatus.AWAITING_FINANCING, "6800000.00");
    approvedLoan(order, "6800000.00");

    service.applyLoanApplicationStatus(
        order.getFinancing().getLoanApplicationId(),
        LoanApplication.LoanApplicationStatus.APPROVED);

    assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.AWAITING_PAYMENT);
    assertThat(order.getFinancing().getFinancingStatus()).isEqualTo(FinancingStatus.APPROVED);
    assertThat(order.getStatusHistory())
        .extracting(h -> h.getToStatus())
        .containsExactly(
            PurchaseOrderStatus.FINANCING_APPROVED, PurchaseOrderStatus.AWAITING_PAYMENT);
  }

  @Test
  void partialApprovalParksTheOrderUntilTheBuyerDecides() {
    PropertyPurchaseOrder order =
        financedOrder(PurchaseOrderStatus.AWAITING_FINANCING, "6800000.00");
    approvedLoan(order, "3000000.00");

    service.applyLoanApplicationStatus(
        order.getFinancing().getLoanApplicationId(),
        LoanApplication.LoanApplicationStatus.APPROVED);

    assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.FINANCING_PARTIALLY_APPROVED);
    assertThat(order.getFinancing().getFinancingStatus())
        .isEqualTo(FinancingStatus.PARTIALLY_APPROVED);
    assertThat(order.getFinancing().getApprovedAmount()).isEqualByComparingTo("3000000.00");
    // the split is untouched until the buyer accepts
    assertThat(order.getFinancing().getFinancedAmount()).isEqualByComparingTo("6800000.00");

    service.acceptPartialApproval(buyer, order.getId());

    assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.AWAITING_PAYMENT);
    PurchaseOrderFinancing f = order.getFinancing();
    assertThat(f.getFinancingStatus()).isEqualTo(FinancingStatus.APPROVED);
    assertThat(f.getFinancedAmount()).isEqualByComparingTo("3000000.00");
    assertThat(f.getCashPortionAmount()).isEqualByComparingTo("5500000.00");
    assertThat(f.getFinancingCoverageRatio()).isEqualByComparingTo("0.3529");
    assertThat(f.getFinancingMode()).isEqualTo(FinancingMode.PARTIAL);
    assertThat(f.getTenureMonths()).isEqualTo(180);
    assertThat(f.getEstimatedMonthlyInstallment())
        .isEqualByComparingTo(
            PropertyFinancingResolver.monthlyInstallment(
                new BigDecimal("3000000.00"), new BigDecimal("14.00"), 180));
  }

  @Test
  void rejectionLetsTheBuyerConvertToCash() {
    PropertyPurchaseOrder order =
        financedOrder(PurchaseOrderStatus.AWAITING_FINANCING, "6800000.00");

    service.applyLoanApplicationStatus(
        order.getFinancing().getLoanApplicationId(),
        LoanApplication.LoanApplicationStatus.REJECTED);
    assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.FINANCING_REJECTED);

    service.convertToCash(buyer, order.getId());
    assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.AWAITING_PAYMENT);
    assertThat(order.getPurchaseType()).isEqualTo(PurchaseType.CASH);
    assertThat(order.getFinancing().getFinancingStatus()).isEqualTo(FinancingStatus.WITHDRAWN);
  }

  @Test
  void convertToCashIsNotAllowedWhileFinancingIsStillPossible() {
    PropertyPurchaseOrder order =
        financedOrder(PurchaseOrderStatus.AWAITING_FINANCING, "6800000.00");
    assertThatThrownBy(() -> service.convertToCash(buyer, order.getId()))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void loanEventsForAnUnknownApplicationAreIgnored() {
    service.applyLoanApplicationStatus(
        UUID.randomUUID(), LoanApplication.LoanApplicationStatus.APPROVED);
    verify(orderRepository, never()).save(any());
  }

  // ------------------------------------------------------------------ expiry

  @Test
  void staleOrdersExpireAndTheirLoansAreWithdrawn() {
    PropertyPurchaseOrder order =
        financedOrder(PurchaseOrderStatus.PENDING_SELLER_REVIEW, "6800000.00");
    when(orderRepository.findByStatusAndExpiresAtBefore(
            eq(PurchaseOrderStatus.PENDING_SELLER_REVIEW), any()))
        .thenReturn(List.of(order));

    assertThat(service.expireStaleOrders()).isEqualTo(1);
    assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.EXPIRED);
    verify(loanApplicationService).withdrawLoanApplication(any(), anyString());
    verify(propertyRepository, never()).save(any());
  }

  @Test
  void illegalTransitionsAreRefused() {
    PropertyPurchaseOrder order = financedOrder(PurchaseOrderStatus.COMPLETED, "6800000.00");
    assertThatThrownBy(() -> service.cancel(buyer, order.getId(), null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("already closed");
    assertThatThrownBy(() -> service.complete(seller, order.getId(), null))
        .isInstanceOf(BusinessException.class);
  }

  // ------------------------------------------------------------------ agreements

  @Test
  void creationSignsThePromiseToPurchaseInTheSameTransaction() {
    CreatePurchaseOrderRequest r = request();
    service.createPurchaseOrder(buyer, r, EVIDENCE);
    ArgumentCaptor<PropertyPurchaseOrder> order =
        ArgumentCaptor.forClass(PropertyPurchaseOrder.class);
    verify(agreementService)
        .signPromiseToPurchaseAtCreation(
            order.capture(), eq(r.getPromiseToPurchase()), eq(EVIDENCE));
    assertThat(order.getValue().getOrderNumber()).startsWith("PPO-");
    verify(orderRepository).save(order.getValue());
  }

  @Test
  void aFailedPromiseSignatureAbortsTheOrder() {
    org.mockito.Mockito.doThrow(new BusinessException("template changed"))
        .when(agreementService)
        .signPromiseToPurchaseAtCreation(any(), any(), any());
    assertThatThrownBy(() -> service.createPurchaseOrder(buyer, request(), EVIDENCE))
        .isInstanceOf(BusinessException.class);
    verify(orderRepository, never()).save(any());
  }

  @Test
  void sellerAcceptanceIssuesTheFollowUpAgreements() {
    PropertyPurchaseOrder order =
        financedOrder(PurchaseOrderStatus.PENDING_SELLER_REVIEW, "6800000.00");
    service.accept(seller, order.getId(), null);
    verify(agreementService).issueForTrigger(order, IssueTrigger.SELLER_ACCEPTANCE);
  }

  @Test
  void financingApprovalIssuesTheFinancingAgreements() {
    PropertyPurchaseOrder order =
        financedOrder(PurchaseOrderStatus.AWAITING_FINANCING, "6800000.00");
    approvedLoan(order, "6800000.00");
    service.applyLoanApplicationStatus(
        order.getFinancing().getLoanApplicationId(),
        LoanApplication.LoanApplicationStatus.APPROVED);
    verify(agreementService).issueForTrigger(order, IssueTrigger.FINANCING_APPROVAL);
  }

  @Test
  void completionIsBlockedWhileAgreementsAreUnsigned() {
    PropertyPurchaseOrder order = financedOrder(PurchaseOrderStatus.AWAITING_PAYMENT, "6800000.00");
    when(agreementService.hasUnsignedBlockingAgreements(order)).thenReturn(true);
    assertThatThrownBy(() -> service.complete(seller, order.getId(), null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("not signed");
    assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.AWAITING_PAYMENT);
  }

  @Test
  void closingAnOrderVoidsItsOpenAgreementsAndConvertingVoidsFinancingOnes() {
    PropertyPurchaseOrder order =
        financedOrder(PurchaseOrderStatus.FINANCING_REJECTED, "6800000.00");
    service.convertToCash(buyer, order.getId());
    verify(agreementService).voidFinancingAgreements(eq(order), anyString());

    PropertyPurchaseOrder other =
        financedOrder(PurchaseOrderStatus.PENDING_SELLER_REVIEW, "6800000.00");
    service.cancel(buyer, other.getId(), "no");
    verify(agreementService).voidOpenAgreements(eq(other), anyString());
  }

  // ------------------------------------------------------------------ profile phone

  @Test
  void theOrderPhoneIsRememberedOnAProfileThatHasNone() {
    User googleBuyer = new User();
    googleBuyer.setId(buyerId);
    googleBuyer.setEmail("buyer@gmail.com");
    when(userRepository.findById(buyerId)).thenReturn(Optional.of(googleBuyer));
    when(userRepository.existsByPhoneNumber("+251911223344")).thenReturn(false);

    service.createPurchaseOrder(buyer, request(), EVIDENCE);

    assertThat(googleBuyer.getPhoneNumber()).isEqualTo("+251911223344");
    verify(userRepository).save(googleBuyer);
  }

  @Test
  void anExistingProfilePhoneOrATakenNumberIsLeftAlone() {
    User withPhone = new User();
    withPhone.setId(buyerId);
    withPhone.setPhoneNumber("+251700000000");
    when(userRepository.findById(buyerId)).thenReturn(Optional.of(withPhone));
    service.createPurchaseOrder(buyer, request(), EVIDENCE);
    assertThat(withPhone.getPhoneNumber()).isEqualTo("+251700000000");

    User noPhone = new User();
    noPhone.setId(buyerId);
    when(userRepository.findById(buyerId)).thenReturn(Optional.of(noPhone));
    when(userRepository.existsByPhoneNumber("+251911223344")).thenReturn(true);
    service.createPurchaseOrder(buyer, request(), EVIDENCE);
    assertThat(noPhone.getPhoneNumber()).isNull();
    verify(userRepository, never()).save(any());
  }
}
