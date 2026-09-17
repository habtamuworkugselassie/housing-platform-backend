package com.housingplatform.purchase.service.impl;

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
import com.housingplatform.purchase.domain.PurchaseOrderStatusHistory;
import com.housingplatform.purchase.dto.CreatePurchaseOrderRequest;
import com.housingplatform.purchase.dto.PurchaseOrderResponse;
import com.housingplatform.purchase.dto.PurchasePreviewResponse;
import com.housingplatform.purchase.dto.UpdatePurchaseFinancingRequest;
import com.housingplatform.purchase.repository.PropertyPurchaseOrderRepository;
import com.housingplatform.purchase.service.PropertyFinancingResolver;
import com.housingplatform.purchase.service.PropertyFinancingResolver.FinancingChoice;
import com.housingplatform.purchase.service.PropertyFinancingResolver.FinancingResolution;
import com.housingplatform.purchase.service.PropertyFinancingResolver.FinancingTerms;
import com.housingplatform.purchase.service.PurchaseAgreementService;
import com.housingplatform.purchase.service.PurchaseOrderAccess;
import com.housingplatform.purchase.service.PurchaseOrderActor;
import com.housingplatform.purchase.service.PurchaseOrderEvents.PurchaseOrderCreatedEvent;
import com.housingplatform.purchase.service.PurchaseOrderEvents.PurchaseOrderStatusChangedEvent;
import com.housingplatform.purchase.service.PurchaseOrderMapper;
import com.housingplatform.purchase.service.PurchaseOrderService;
import com.housingplatform.purchase.service.SignatureEvidence;
import com.housingplatform.shared.domain.Currency;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.DuplicateResourceException;
import com.housingplatform.shared.exception.ForbiddenOperationException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import com.housingplatform.shared.util.PhoneNumberNormalizer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

  static final Duration SELLER_REVIEW_WINDOW = Duration.ofDays(14);
  static final String SYSTEM_ACTOR = "system";

  /** The edges of the order state machine. Anything not listed here is an illegal transition. */
  private static final Map<PurchaseOrderStatus, Set<PurchaseOrderStatus>> ALLOWED =
      Map.of(
          PurchaseOrderStatus.PENDING_SELLER_REVIEW,
          EnumSet.of(
              PurchaseOrderStatus.AWAITING_FINANCING,
              PurchaseOrderStatus.AWAITING_PAYMENT,
              PurchaseOrderStatus.REJECTED,
              PurchaseOrderStatus.CANCELLED,
              PurchaseOrderStatus.EXPIRED),
          PurchaseOrderStatus.AWAITING_FINANCING,
          EnumSet.of(
              PurchaseOrderStatus.FINANCING_APPROVED,
              PurchaseOrderStatus.FINANCING_PARTIALLY_APPROVED,
              PurchaseOrderStatus.FINANCING_REJECTED,
              PurchaseOrderStatus.CANCELLED,
              PurchaseOrderStatus.REJECTED),
          PurchaseOrderStatus.FINANCING_APPROVED,
          EnumSet.of(
              PurchaseOrderStatus.AWAITING_PAYMENT,
              PurchaseOrderStatus.CANCELLED,
              PurchaseOrderStatus.REJECTED),
          PurchaseOrderStatus.FINANCING_PARTIALLY_APPROVED,
          EnumSet.of(
              PurchaseOrderStatus.FINANCING_APPROVED,
              PurchaseOrderStatus.AWAITING_PAYMENT,
              PurchaseOrderStatus.CANCELLED,
              PurchaseOrderStatus.REJECTED),
          PurchaseOrderStatus.FINANCING_REJECTED,
          EnumSet.of(
              PurchaseOrderStatus.AWAITING_PAYMENT,
              PurchaseOrderStatus.AWAITING_FINANCING,
              PurchaseOrderStatus.CANCELLED,
              PurchaseOrderStatus.REJECTED),
          PurchaseOrderStatus.AWAITING_PAYMENT,
          EnumSet.of(
              PurchaseOrderStatus.COMPLETED,
              PurchaseOrderStatus.CANCELLED,
              PurchaseOrderStatus.REJECTED));

  private final PropertyPurchaseOrderRepository orderRepository;
  private final PropertyRepository propertyRepository;
  private final UserRepository userRepository;
  private final PropertyFinancingResolver financingResolver;
  private final LoanApplicationService loanApplicationService;
  private final PurchaseOrderMapper mapper;
  private final PurchaseAgreementService agreementService;
  private final ApplicationEventPublisher eventPublisher;
  private final CacheManager cacheManager;

  // ------------------------------------------------------------------ preview / create

  @Override
  @Transactional(readOnly = true)
  public PurchasePreviewResponse preview(
      PurchaseOrderActor buyer, UUID propertyId, Currency currency) {
    Property property = loadProperty(propertyId);
    Currency effective = currency != null ? currency : Currency.ETB;
    BigDecimal price = resolvePrice(property, effective);
    PurchasePreviewResponse preview =
        mapper.toPreview(
            property, effective, price, financingResolver.listEligible(property, effective, price));
    FinancingTerms defaultTerms =
        preview.isFinancingAvailable()
            ? financingResolver
                .resolve(property, effective, price, null, FinancingChoice.none())
                .terms()
            : null;
    preview.setAgreementsToSign(
        agreementService.previewOrderCreationAgreements(
            property, buyer.userId(), effective, price, defaultTerms));
    return preview;
  }

  @Override
  public PurchaseOrderResponse createPurchaseOrder(
      PurchaseOrderActor buyer, CreatePurchaseOrderRequest request, SignatureEvidence evidence) {
    Property property = loadProperty(request.getPropertyId());
    guardPurchasable(property);
    guardNotOwnListing(buyer, property);

    if (orderRepository.existsByBuyerIdAndPropertyIdAndStatusIn(
        buyer.userId(), property.getId(), PurchaseOrderStatus.OPEN)) {
      throw new DuplicateResourceException(
          "You already have an open purchase order for this property");
    }

    Currency currency = request.getCurrency() != null ? request.getCurrency() : Currency.ETB;
    BigDecimal price = resolvePrice(property, currency);
    String phone = PhoneNumberNormalizer.toE164(request.getContactPhone());
    String email = normaliseEmail(request.getContactEmail());

    FinancingResolution resolution =
        financingResolver.resolve(
            property, currency, price, request.getUseFinancing(), toChoice(request.getFinancing()));

    List<String> warnings = new ArrayList<>();
    if (!resolution.applied() && request.getFinancing() != null) {
      warnings.add(
          resolution.notAppliedReason() == PropertyFinancingResolver.NotAppliedReason.BUYER_DECLINED
              ? "financing block ignored: useFinancing is false"
              : "financing block ignored: property has no active financing product");
    }

    LocalDateTime now = LocalDateTime.now();
    PropertyPurchaseOrder order =
        PropertyPurchaseOrder.builder()
            .orderNumber(newOrderNumber())
            .propertyId(property.getId())
            .buyerId(buyer.userId())
            .realEstateCompanyId(property.getRealEstateCompanyId())
            .agentId(property.getAgentId())
            .contactPhone(phone)
            .contactEmail(email)
            .purchaseType(resolution.applied() ? PurchaseType.BANK_FINANCED : PurchaseType.CASH)
            .status(PurchaseOrderStatus.PENDING_SELLER_REVIEW)
            .listedPrice(price)
            .currency(currency)
            .buyerMessage(blankToNull(request.getBuyerMessage()))
            .expiresAt(now.plus(SELLER_REVIEW_WINDOW))
            .build();

    if (resolution.applied()) {
      FinancingTerms terms = resolution.terms();
      LoanApplicationResponse loan = openLoanApplication(buyer.userId(), order, terms);
      order.setFinancing(newFinancing(order, terms, loan.getId()));
    }

    // The Promise to Purchase between buyer and provider is part of the same transaction: no
    // order exists without it.
    agreementService.signPromiseToPurchaseAtCreation(
        order, request.getPromiseToPurchase(), evidence);

    order
        .getStatusHistory()
        .add(history(order, null, order.getStatus(), buyer.userId(), null, now));
    PropertyPurchaseOrder saved = orderRepository.save(order);
    eventPublisher.publishEvent(new PurchaseOrderCreatedEvent(saved.getId()));
    rememberBuyerPhone(buyer.userId(), phone);
    return mapper.toResponse(saved, warnings);
  }

  // ------------------------------------------------------------------ reads

  @Override
  @Transactional(readOnly = true)
  public PurchaseOrderResponse getPurchaseOrder(PurchaseOrderActor actor, UUID orderId) {
    PropertyPurchaseOrder order = loadOrder(orderId);
    if (!PurchaseOrderAccess.canView(actor, order)) {
      // Existence of somebody else's order is itself information: answer as if it were absent.
      throw new ResourceNotFoundException("PurchaseOrder", orderId);
    }
    return mapper.toResponse(order);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<PurchaseOrderResponse> getMyPurchaseOrders(
      PurchaseOrderActor buyer, PurchaseOrderStatus status, Pageable pageable) {
    Page<PropertyPurchaseOrder> page =
        status == null
            ? orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyer.userId(), pageable)
            : orderRepository.findByBuyerIdAndStatusOrderByCreatedAtDesc(
                buyer.userId(), status, pageable);
    return page.map(mapper::toResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public List<PurchaseOrderResponse> getPurchaseOrdersForProperty(
      PurchaseOrderActor seller, UUID propertyId, PurchaseOrderStatus status) {
    Property property = loadProperty(propertyId);
    if (!seller.admin()
        && !PurchaseOrderAccess.sameOrganization(seller, property.getRealEstateCompanyId())
        && !PurchaseOrderAccess.sameAgent(seller, property.getAgentId())) {
      throw new ForbiddenOperationException("You do not manage this property");
    }
    List<PropertyPurchaseOrder> orders =
        status == null
            ? orderRepository.findByPropertyIdOrderByCreatedAtDesc(propertyId)
            : orderRepository.findByPropertyIdAndStatusOrderByCreatedAtDesc(propertyId, status);
    return orders.stream().map(mapper::toResponse).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Page<PurchaseOrderResponse> getReceivedPurchaseOrders(
      PurchaseOrderActor seller, PurchaseOrderStatus status, Pageable pageable) {
    if (seller.organizationId() == null) {
      throw new ForbiddenOperationException(
          "Your account is not attached to a real estate company");
    }
    Page<PropertyPurchaseOrder> page =
        status == null
            ? orderRepository.findByRealEstateCompanyIdOrderByCreatedAtDesc(
                seller.organizationId(), pageable)
            : orderRepository.findByRealEstateCompanyIdAndStatusOrderByCreatedAtDesc(
                seller.organizationId(), status, pageable);
    return page.map(mapper::toResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<PurchaseOrderResponse> getFinancedPurchaseOrders(
      PurchaseOrderActor banker, PurchaseOrderStatus status, Pageable pageable) {
    if (banker.organizationId() == null) {
      throw new ForbiddenOperationException("Your account is not attached to a bank");
    }
    return orderRepository
        .findFinancedByBank(banker.organizationId(), status, pageable)
        .map(mapper::toResponse);
  }

  // ------------------------------------------------------------------ buyer actions

  @Override
  public PurchaseOrderResponse updateFinancing(
      PurchaseOrderActor buyer, UUID orderId, UpdatePurchaseFinancingRequest request) {
    PropertyPurchaseOrder order = loadOwnOrder(buyer, orderId);
    PurchaseOrderFinancing financing = requireFinancing(order);
    Property property = loadProperty(order.getPropertyId());
    FinancingChoice choice =
        new FinancingChoice(
            financing.getFinancingOfferId(),
            request.getFinancedAmount(),
            request.getDownPaymentAmount(),
            request.getRequestedTenureMonths() != null
                ? request.getRequestedTenureMonths()
                : financing.getTenureMonths());

    switch (order.getStatus()) {
      case PENDING_SELLER_REVIEW -> {
        FinancingTerms terms =
            financingResolver.recompute(
                property,
                order.getCurrency(),
                order.getListedPrice(),
                financing.getFinancingOfferId(),
                choice);
        applyTerms(financing, terms);
        loanApplicationService.updateRequestedTerms(
            financing.getLoanApplicationId(), terms.financedAmount(), terms.tenureMonths());
      }
      case FINANCING_REJECTED -> {
        FinancingTerms terms =
            financingResolver.recompute(
                property,
                order.getCurrency(),
                order.getListedPrice(),
                financing.getFinancingOfferId(),
                choice);
        if (terms.financedAmount().compareTo(financing.getFinancedAmount()) >= 0) {
          throw new BusinessException(
              "After a rejection you can only re-apply for a smaller amount than "
                  + financing.getFinancedAmount().toPlainString());
        }
        applyTerms(financing, terms);
        LoanApplicationResponse loan = openLoanApplication(buyer.userId(), order, terms);
        financing.setLoanApplicationId(loan.getId());
        financing.setFinancingStatus(FinancingStatus.APPLICATION_SUBMITTED);
        financing.setApprovedAmount(null);
        financing.setApprovedInterestRate(null);
        financing.setApprovedTenureMonths(null);
        transition(
            order,
            PurchaseOrderStatus.AWAITING_FINANCING,
            buyer.userId().toString(),
            "Re-applied for " + terms.financedAmount().toPlainString());
      }
      default -> throw new BusinessException(
          "Financing can only be changed while the order awaits seller review or after a"
              + " financing rejection (current status: "
              + order.getStatus()
              + ")");
    }
    return mapper.toResponse(orderRepository.save(order));
  }

  @Override
  public PurchaseOrderResponse cancel(PurchaseOrderActor buyer, UUID orderId, String reason) {
    PropertyPurchaseOrder order = loadOwnOrder(buyer, orderId);
    if (order.getStatus().isTerminal()) {
      throw new BusinessException("Order " + order.getOrderNumber() + " is already closed");
    }
    order.setCancellationReason(blankToNull(reason));
    closeOrder(order, PurchaseOrderStatus.CANCELLED, buyer.userId().toString(), reason);
    return mapper.toResponse(orderRepository.save(order));
  }

  @Override
  public PurchaseOrderResponse acceptPartialApproval(PurchaseOrderActor buyer, UUID orderId) {
    PropertyPurchaseOrder order = loadOwnOrder(buyer, orderId);
    requireStatus(order, PurchaseOrderStatus.FINANCING_PARTIALLY_APPROVED);
    PurchaseOrderFinancing f = requireFinancing(order);
    BigDecimal approved = f.getApprovedAmount();
    if (approved == null) {
      throw new BusinessException("The bank's approved amount is not recorded on this order");
    }
    f.setFinancedAmount(approved);
    f.setCashPortionAmount(order.getListedPrice().subtract(approved));
    f.setFinancingCoverageRatio(approved.divide(order.getListedPrice(), 4, RoundingMode.HALF_UP));
    f.setFinancingMode(FinancingMode.PARTIAL);
    int tenure =
        f.getApprovedTenureMonths() != null ? f.getApprovedTenureMonths() : f.getTenureMonths();
    BigDecimal rate =
        f.getApprovedInterestRate() != null
            ? f.getApprovedInterestRate()
            : f.getAppliedInterestRate();
    f.setTenureMonths(tenure);
    f.setEstimatedMonthlyInstallment(
        PropertyFinancingResolver.monthlyInstallment(approved, rate, tenure));
    f.setFinancingStatus(FinancingStatus.APPROVED);

    String actor = buyer.userId().toString();
    transition(
        order,
        PurchaseOrderStatus.FINANCING_APPROVED,
        actor,
        "Buyer accepted partial approval of " + approved.toPlainString());
    agreementService.issueForTrigger(order, IssueTrigger.FINANCING_APPROVAL);
    transition(order, PurchaseOrderStatus.AWAITING_PAYMENT, actor, null);
    return mapper.toResponse(orderRepository.save(order));
  }

  @Override
  public PurchaseOrderResponse convertToCash(PurchaseOrderActor buyer, UUID orderId) {
    PropertyPurchaseOrder order = loadOwnOrder(buyer, orderId);
    if (order.getStatus() != PurchaseOrderStatus.FINANCING_REJECTED
        && order.getStatus() != PurchaseOrderStatus.FINANCING_PARTIALLY_APPROVED) {
      throw new BusinessException(
          "An order can be converted to cash only after financing was rejected or partially approved");
    }
    PurchaseOrderFinancing f = requireFinancing(order);
    withdrawLoanQuietly(f, "Buyer converted purchase order " + order.getOrderNumber() + " to cash");
    f.setFinancingStatus(FinancingStatus.WITHDRAWN);
    order.setPurchaseType(PurchaseType.CASH);
    agreementService.voidFinancingAgreements(order, "Order converted to cash purchase");
    transition(
        order,
        PurchaseOrderStatus.AWAITING_PAYMENT,
        buyer.userId().toString(),
        "Converted to cash purchase");
    return mapper.toResponse(orderRepository.save(order));
  }

  // ------------------------------------------------------------------ seller actions

  @Override
  public PurchaseOrderResponse accept(PurchaseOrderActor seller, UUID orderId, String notes) {
    PropertyPurchaseOrder order = loadManagedOrder(seller, orderId);
    requireStatus(order, PurchaseOrderStatus.PENDING_SELLER_REVIEW);
    PurchaseOrderStatus next =
        order.isFinanced()
            ? PurchaseOrderStatus.AWAITING_FINANCING
            : PurchaseOrderStatus.AWAITING_PAYMENT;
    order.setExpiresAt(null);
    transition(order, next, seller.userId().toString(), blankToNull(notes));
    agreementService.issueForTrigger(order, IssueTrigger.SELLER_ACCEPTANCE);
    reserveProperty(order.getPropertyId());
    return mapper.toResponse(orderRepository.save(order));
  }

  @Override
  public PurchaseOrderResponse reject(PurchaseOrderActor seller, UUID orderId, String reason) {
    PropertyPurchaseOrder order = loadManagedOrder(seller, orderId);
    if (order.getStatus().isTerminal()) {
      throw new BusinessException("Order " + order.getOrderNumber() + " is already closed");
    }
    order.setRejectionReason(reason);
    closeOrder(order, PurchaseOrderStatus.REJECTED, seller.userId().toString(), reason);
    return mapper.toResponse(orderRepository.save(order));
  }

  @Override
  public PurchaseOrderResponse complete(
      PurchaseOrderActor seller, UUID orderId, String paymentReference) {
    PropertyPurchaseOrder order = loadManagedOrder(seller, orderId);
    requireStatus(order, PurchaseOrderStatus.AWAITING_PAYMENT);
    if (agreementService.hasUnsignedBlockingAgreements(order)) {
      throw new BusinessException(
          "Order "
              + order.getOrderNumber()
              + " has agreements the buyer has not signed yet; the sale cannot be completed");
    }
    order.setPaymentReference(blankToNull(paymentReference));
    String actor = seller.userId().toString();
    transition(order, PurchaseOrderStatus.COMPLETED, actor, blankToNull(paymentReference));
    PropertyPurchaseOrder saved = orderRepository.save(order);

    updatePropertyStatus(order.getPropertyId(), Property.PropertyStatus.SOLD);
    for (PropertyPurchaseOrder other :
        orderRepository.findByPropertyIdAndStatusIn(
            order.getPropertyId(), PurchaseOrderStatus.OPEN)) {
      if (!other.getId().equals(saved.getId())) {
        other.setRejectionReason("PROPERTY_SOLD");
        closeOrder(
            other, PurchaseOrderStatus.REJECTED, SYSTEM_ACTOR, "Property sold to another buyer");
        orderRepository.save(other);
      }
    }
    return mapper.toResponse(saved);
  }

  // ------------------------------------------------------------------ system

  @Override
  public int expireStaleOrders() {
    List<PropertyPurchaseOrder> stale =
        orderRepository.findByStatusAndExpiresAtBefore(
            PurchaseOrderStatus.PENDING_SELLER_REVIEW, LocalDateTime.now());
    for (PropertyPurchaseOrder order : stale) {
      closeOrder(
          order, PurchaseOrderStatus.EXPIRED, SYSTEM_ACTOR, "Seller did not respond in time");
      orderRepository.save(order);
    }
    return stale.size();
  }

  @Override
  public void applyLoanApplicationStatus(
      UUID loanApplicationId, LoanApplication.LoanApplicationStatus loanStatus) {
    PropertyPurchaseOrder order =
        orderRepository.findByLoanApplicationId(loanApplicationId).orElse(null);
    if (order == null || order.getFinancing() == null) {
      return;
    }
    PurchaseOrderFinancing f = order.getFinancing();
    if (!loanApplicationId.equals(f.getLoanApplicationId())) {
      return; // an older application of this order; the current one is what counts
    }
    switch (loanStatus) {
      case UNDER_REVIEW -> f.setFinancingStatus(FinancingStatus.UNDER_REVIEW);
      case APPROVED -> {
        LoanApplicationResponse loan =
            loanApplicationService.getLoanApplicationById(loanApplicationId);
        f.setApprovedAmount(loan.getApprovedAmount());
        f.setApprovedInterestRate(loan.getApprovedInterestRate());
        f.setApprovedTenureMonths(loan.getApprovedTenureMonths());
        boolean shortfall =
            loan.getApprovedAmount() != null
                && loan.getApprovedAmount().compareTo(f.getFinancedAmount()) < 0;
        if (order.getStatus() == PurchaseOrderStatus.AWAITING_FINANCING) {
          if (shortfall) {
            f.setFinancingStatus(FinancingStatus.PARTIALLY_APPROVED);
            transition(
                order,
                PurchaseOrderStatus.FINANCING_PARTIALLY_APPROVED,
                SYSTEM_ACTOR,
                "Bank approved " + loan.getApprovedAmount().toPlainString());
          } else {
            f.setFinancingStatus(FinancingStatus.APPROVED);
            transition(
                order, PurchaseOrderStatus.FINANCING_APPROVED, SYSTEM_ACTOR, "Bank approved");
            agreementService.issueForTrigger(order, IssueTrigger.FINANCING_APPROVAL);
            transition(order, PurchaseOrderStatus.AWAITING_PAYMENT, SYSTEM_ACTOR, null);
          }
        } else {
          f.setFinancingStatus(
              shortfall ? FinancingStatus.PARTIALLY_APPROVED : FinancingStatus.APPROVED);
        }
      }
      case REJECTED -> {
        f.setFinancingStatus(FinancingStatus.REJECTED);
        if (order.getStatus() == PurchaseOrderStatus.AWAITING_FINANCING) {
          transition(order, PurchaseOrderStatus.FINANCING_REJECTED, SYSTEM_ACTOR, "Bank rejected");
        }
      }
      case DISBURSED -> f.setFinancingStatus(FinancingStatus.DISBURSED);
      case CLOSED -> {
        if (f.getFinancingStatus() != FinancingStatus.REJECTED
            && f.getFinancingStatus() != FinancingStatus.DISBURSED) {
          f.setFinancingStatus(FinancingStatus.WITHDRAWN);
        }
      }
      default -> {}
    }
    orderRepository.save(order);
  }

  // ------------------------------------------------------------------ helpers

  private void closeOrder(
      PropertyPurchaseOrder order, PurchaseOrderStatus terminal, String actor, String notes) {
    boolean hadReservation = order.getStatus() != PurchaseOrderStatus.PENDING_SELLER_REVIEW;
    if (order.getFinancing() != null) {
      withdrawLoanQuietly(order.getFinancing(), notes != null ? notes : terminal.name());
      if (order.getFinancing().getFinancingStatus() != FinancingStatus.REJECTED
          && order.getFinancing().getFinancingStatus() != FinancingStatus.DISBURSED) {
        order.getFinancing().setFinancingStatus(FinancingStatus.WITHDRAWN);
      }
    }
    transition(order, terminal, actor, blankToNull(notes));
    agreementService.voidOpenAgreements(order, "Order " + terminal.name().toLowerCase(Locale.ROOT));
    if (hadReservation) {
      releaseReservationIfUnused(order.getPropertyId(), order.getId());
    }
  }

  void transition(PropertyPurchaseOrder order, PurchaseOrderStatus to, String actor, String notes) {
    PurchaseOrderStatus from = order.getStatus();
    if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
      throw new BusinessException(
          String.format("Order %s cannot move from %s to %s", order.getOrderNumber(), from, to));
    }
    order.setStatus(to);
    order.getStatusHistory().add(history(order, from, to, actor, notes, LocalDateTime.now()));
    eventPublisher.publishEvent(new PurchaseOrderStatusChangedEvent(order.getId(), from, to));
  }

  private static PurchaseOrderStatusHistory history(
      PropertyPurchaseOrder order,
      PurchaseOrderStatus from,
      PurchaseOrderStatus to,
      Object actor,
      String notes,
      LocalDateTime at) {
    return PurchaseOrderStatusHistory.builder()
        .purchaseOrder(order)
        .fromStatus(from)
        .toStatus(to)
        .changedBy(actor != null ? actor.toString() : null)
        .changedAt(at)
        .notes(notes)
        .build();
  }

  private LoanApplicationResponse openLoanApplication(
      UUID buyerId, PropertyPurchaseOrder order, FinancingTerms terms) {
    LoanApplicationRequest loan = new LoanApplicationRequest();
    loan.setBankId(terms.eligible().offer().getBankId());
    loan.setCreditProductId(terms.eligible().product().getId());
    loan.setFinancingOfferId(terms.eligible().offer().getId());
    loan.setPropertyId(order.getPropertyId());
    loan.setRequestedAmount(terms.financedAmount());
    loan.setCurrency(order.getCurrency());
    loan.setRequestedTenureMonths(terms.tenureMonths());
    loan.setPurpose("Purchase order " + order.getOrderNumber());
    return loanApplicationService.createLoanApplication(buyerId, loan);
  }

  private static PurchaseOrderFinancing newFinancing(
      PropertyPurchaseOrder order, FinancingTerms terms, UUID loanApplicationId) {
    PurchaseOrderFinancing f =
        PurchaseOrderFinancing.builder()
            .purchaseOrder(order)
            .financingOfferId(terms.eligible().offer().getId())
            .bankId(terms.eligible().offer().getBankId())
            .creditProductId(terms.eligible().product().getId())
            .offerLevel(terms.eligible().level())
            .appliedInterestRate(terms.eligible().interestRate())
            .appliedLtvRatio(terms.eligible().ltvRatio())
            .minFinanceableAmount(terms.eligible().minFinanceable())
            .maxFinanceableAmount(terms.eligible().maxFinanceable())
            .loanApplicationId(loanApplicationId)
            .financingStatus(FinancingStatus.APPLICATION_SUBMITTED)
            .build();
    applyTerms(f, terms);
    return f;
  }

  private static void applyTerms(PurchaseOrderFinancing f, FinancingTerms terms) {
    f.setFinancingMode(terms.mode());
    f.setFinancedAmount(terms.financedAmount());
    f.setCashPortionAmount(terms.cashPortion());
    f.setFinancingCoverageRatio(terms.coverageRatio());
    f.setTenureMonths(terms.tenureMonths());
    f.setEstimatedMonthlyInstallment(terms.estimatedMonthlyInstallment());
  }

  private void withdrawLoanQuietly(PurchaseOrderFinancing f, String reason) {
    if (f.getLoanApplicationId() == null) {
      return;
    }
    try {
      loanApplicationService.withdrawLoanApplication(f.getLoanApplicationId(), reason);
    } catch (ResourceNotFoundException e) {
      log.warn(
          "Loan application {} linked to a purchase order no longer exists",
          f.getLoanApplicationId());
    }
  }

  private static FinancingChoice toChoice(CreatePurchaseOrderRequest.FinancingSelection s) {
    if (s == null) {
      return FinancingChoice.none();
    }
    return new FinancingChoice(
        s.getFinancingOfferId(),
        s.getFinancedAmount(),
        s.getDownPaymentAmount(),
        s.getRequestedTenureMonths());
  }

  private Property loadProperty(UUID propertyId) {
    return propertyRepository
        .findById(propertyId)
        .orElseThrow(() -> new ResourceNotFoundException("Property", propertyId));
  }

  private PropertyPurchaseOrder loadOrder(UUID orderId) {
    return orderRepository
        .findById(orderId)
        .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", orderId));
  }

  private PropertyPurchaseOrder loadOwnOrder(PurchaseOrderActor buyer, UUID orderId) {
    PropertyPurchaseOrder order = loadOrder(orderId);
    if (!buyer.admin() && !order.getBuyerId().equals(buyer.userId())) {
      throw new ResourceNotFoundException("PurchaseOrder", orderId);
    }
    return order;
  }

  private PropertyPurchaseOrder loadManagedOrder(PurchaseOrderActor seller, UUID orderId) {
    PropertyPurchaseOrder order = loadOrder(orderId);
    if (!PurchaseOrderAccess.canSell(seller, order)) {
      if (PurchaseOrderAccess.canView(seller, order)) {
        throw new ForbiddenOperationException("Only the listing's agent or company can do this");
      }
      throw new ResourceNotFoundException("PurchaseOrder", orderId);
    }
    return order;
  }

  private static void guardPurchasable(Property property) {
    if (property.getCategory() != Property.PropertyCategory.FOR_SALE) {
      throw new BusinessException("This property is not listed for sale");
    }
    if (property.getStatus() != Property.PropertyStatus.AVAILABLE) {
      throw new BusinessException(
          "This property is not available for purchase (status: " + property.getStatus() + ")");
    }
    if (property.getVerificationStatus() != Property.VerificationStatus.VERIFIED) {
      throw new BusinessException("This property has not been verified yet");
    }
  }

  private static void guardNotOwnListing(PurchaseOrderActor buyer, Property property) {
    if (PurchaseOrderAccess.sameAgent(buyer, property.getAgentId())
        || PurchaseOrderAccess.sameOrganization(buyer, property.getRealEstateCompanyId())) {
      throw new ForbiddenOperationException(
          "You cannot place a purchase order on your own listing");
    }
  }

  private static BigDecimal resolvePrice(Property property, Currency currency) {
    BigDecimal price = currency == Currency.USD ? property.getPriceUSD() : property.getPriceETB();
    if (price == null || price.signum() <= 0) {
      throw new BusinessException("This property has no price in " + currency.getCode());
    }
    return price.setScale(2, RoundingMode.HALF_UP);
  }

  private static PurchaseOrderFinancing requireFinancing(PropertyPurchaseOrder order) {
    if (order.getFinancing() == null) {
      throw new BusinessException("Order " + order.getOrderNumber() + " has no bank financing");
    }
    return order.getFinancing();
  }

  private static void requireStatus(PropertyPurchaseOrder order, PurchaseOrderStatus expected) {
    if (order.getStatus() != expected) {
      throw new BusinessException(
          String.format(
              "Order %s must be in %s for this action (current status: %s)",
              order.getOrderNumber(), expected, order.getStatus()));
    }
  }

  private void reserveProperty(UUID propertyId) {
    propertyRepository
        .findById(propertyId)
        .filter(p -> p.getStatus() == Property.PropertyStatus.AVAILABLE)
        .ifPresent(p -> updatePropertyStatus(p, Property.PropertyStatus.RESERVED));
  }

  private void releaseReservationIfUnused(UUID propertyId, UUID closingOrderId) {
    boolean othersHoldIt =
        orderRepository.findByPropertyIdAndStatusIn(propertyId, PurchaseOrderStatus.OPEN).stream()
            .anyMatch(
                o ->
                    !o.getId().equals(closingOrderId)
                        && o.getStatus() != PurchaseOrderStatus.PENDING_SELLER_REVIEW);
    if (othersHoldIt) {
      return;
    }
    propertyRepository
        .findById(propertyId)
        .filter(p -> p.getStatus() == Property.PropertyStatus.RESERVED)
        .ifPresent(p -> updatePropertyStatus(p, Property.PropertyStatus.AVAILABLE));
  }

  private void updatePropertyStatus(UUID propertyId, Property.PropertyStatus status) {
    propertyRepository.findById(propertyId).ifPresent(p -> updatePropertyStatus(p, status));
  }

  private void updatePropertyStatus(Property property, Property.PropertyStatus status) {
    property.setStatus(status);
    propertyRepository.save(property);
    Cache cache = cacheManager.getCache("properties");
    if (cache != null) {
      cache.evict(property.getId());
    }
  }

  /**
   * Buyers who joined through Google (or an old email-only profile) have no phone on file. The
   * number they just gave for the order becomes theirs, which also unlocks WhatsApp-code sign-in.
   */
  private void rememberBuyerPhone(UUID buyerId, String phone) {
    userRepository
        .findById(buyerId)
        .filter(u -> u.getPhoneNumber() == null || u.getPhoneNumber().isBlank())
        .filter(u -> !userRepository.existsByPhoneNumber(phone))
        .ifPresent(
            u -> {
              u.setPhoneNumber(phone);
              userRepository.save(u);
            });
  }

  static String newOrderNumber() {
    String random =
        UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    return "PPO-" + Year.now().getValue() + "-" + random;
  }

  private static String normaliseEmail(String email) {
    String trimmed = blankToNull(email);
    return trimmed != null ? trimmed.toLowerCase(Locale.ROOT) : null;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
