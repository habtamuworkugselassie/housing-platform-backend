package com.housingplatform.purchase.service;

import com.housingplatform.banking.repository.CreditProductRepository;
import com.housingplatform.identity.repository.OrganizationRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.property.domain.Property;
import com.housingplatform.property.repository.PropertyRepository;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder.PurchaseOrderStatus;
import com.housingplatform.purchase.domain.PurchaseOrderFinancing;
import com.housingplatform.purchase.domain.PurchaseOrderFinancing.FinancingStatus;
import com.housingplatform.purchase.dto.PurchaseOrderResponse;
import com.housingplatform.purchase.dto.PurchasePreviewResponse;
import com.housingplatform.purchase.service.PropertyFinancingResolver.EligibleOffer;
import com.housingplatform.shared.domain.Currency;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Hand-written mapper: the response joins four aggregates, which MapStruct would only obscure. */
@Component
@RequiredArgsConstructor
public class PurchaseOrderMapper {

  private final PropertyRepository propertyRepository;
  private final OrganizationRepository organizationRepository;
  private final UserRepository userRepository;
  private final CreditProductRepository creditProductRepository;
  private final PurchaseAgreementMapper agreementMapper;
  private final PurchaseDepositService depositService;

  public PurchaseOrderResponse toResponse(PropertyPurchaseOrder order) {
    return toResponse(order, List.of());
  }

  public PurchaseOrderResponse toResponse(PropertyPurchaseOrder order, List<String> warnings) {
    Property property = propertyRepository.findById(order.getPropertyId()).orElse(null);
    return PurchaseOrderResponse.builder()
        .id(order.getId())
        .orderNumber(order.getOrderNumber())
        .status(order.getStatus())
        .purchaseType(order.getPurchaseType())
        .property(
            PurchaseOrderResponse.PropertySummary.builder()
                .id(order.getPropertyId())
                .title(property != null ? property.getTitle() : null)
                .city(property != null ? property.getCity() : null)
                .unitNumber(property != null ? property.getUnitNumber() : null)
                .realEstateCompanyId(order.getRealEstateCompanyId())
                .realEstateCompanyName(organizationName(order.getRealEstateCompanyId()))
                .agentId(order.getAgentId())
                .build())
        .buyer(
            PurchaseOrderResponse.BuyerContact.builder()
                .id(order.getBuyerId())
                .fullName(userFullName(order.getBuyerId()))
                .contactPhone(order.getContactPhone())
                .contactEmail(order.getContactEmail())
                .build())
        .pricing(
            PurchaseOrderResponse.Pricing.builder()
                .listedPrice(order.getListedPrice())
                .currency(order.getCurrency())
                .build())
        .financing(order.getFinancing() != null ? toFinancing(order) : null)
        .buyerMessage(order.getBuyerMessage())
        .expiresAt(order.getExpiresAt())
        .cancellationReason(order.getCancellationReason())
        .rejectionReason(order.getRejectionReason())
        .paymentReference(order.getPaymentReference())
        .warnings(warnings != null ? warnings : List.of())
        .agreements(
            order.getAgreements().stream().map(a -> agreementMapper.toResponse(a, false)).toList())
        .pendingSignatures(
            (int)
                order.getAgreements().stream()
                    .filter(
                        a ->
                            a.getStatus()
                                == com.housingplatform.purchase.domain.PurchaseAgreement
                                    .AgreementStatus.PENDING_BUYER_SIGNATURE)
                    .count())
        .deposit(depositService.toResponse(order))
        .createdAt(order.getCreatedAt())
        .updatedAt(order.getUpdatedAt())
        .statusHistory(
            order.getStatusHistory().stream()
                .map(
                    h ->
                        PurchaseOrderResponse.StatusHistoryEntry.builder()
                            .fromStatus(h.getFromStatus())
                            .toStatus(h.getToStatus())
                            .changedBy(h.getChangedBy())
                            .changedAt(h.getChangedAt())
                            .notes(h.getNotes())
                            .build())
                .toList())
        .build();
  }

  private PurchaseOrderResponse.FinancingDetails toFinancing(PropertyPurchaseOrder order) {
    PurchaseOrderFinancing f = order.getFinancing();
    BigDecimal proposedCash = null;
    if (f.getFinancingStatus() == FinancingStatus.PARTIALLY_APPROVED
        && f.getApprovedAmount() != null) {
      proposedCash = order.getListedPrice().subtract(f.getApprovedAmount());
    }
    return PurchaseOrderResponse.FinancingDetails.builder()
        .financingStatus(f.getFinancingStatus())
        .financingOfferId(f.getFinancingOfferId())
        .offerLevel(f.getOfferLevel())
        .bankId(f.getBankId())
        .bankName(organizationName(f.getBankId()))
        .creditProductId(f.getCreditProductId())
        .creditProductName(
            creditProductRepository
                .findById(f.getCreditProductId())
                .map(p -> p.getName())
                .orElse(null))
        .appliedInterestRate(f.getAppliedInterestRate())
        .appliedLtvRatio(f.getAppliedLtvRatio())
        .minFinanceableAmount(f.getMinFinanceableAmount())
        .maxFinanceableAmount(f.getMaxFinanceableAmount())
        .financingMode(f.getFinancingMode())
        .financedAmount(f.getFinancedAmount())
        .cashPortionAmount(f.getCashPortionAmount())
        .financingCoverageRatio(f.getFinancingCoverageRatio())
        .tenureMonths(f.getTenureMonths())
        .estimatedMonthlyInstallment(f.getEstimatedMonthlyInstallment())
        .loanApplicationId(f.getLoanApplicationId())
        .approvedAmount(f.getApprovedAmount())
        .approvedInterestRate(f.getApprovedInterestRate())
        .approvedTenureMonths(f.getApprovedTenureMonths())
        .proposedCashPortionAmount(proposedCash)
        .nextSteps(nextSteps(order))
        .build();
  }

  private static List<String> nextSteps(PropertyPurchaseOrder order) {
    List<String> steps = new ArrayList<>();
    PurchaseOrderStatus status = order.getStatus();
    FinancingStatus financing = order.getFinancing().getFinancingStatus();
    switch (status) {
      case PENDING_SELLER_REVIEW -> {
        steps.add("Await the seller's review of your order");
        if (financing == FinancingStatus.APPLICATION_SUBMITTED) {
          steps.add("Upload income documents to the loan application");
        }
      }
      case AWAITING_FINANCING -> steps.add("Await the bank's decision on the loan application");
      case FINANCING_PARTIALLY_APPROVED -> steps.add(
          "The bank approved a smaller loan: accept the larger cash portion, convert to a cash purchase, or cancel");
      case FINANCING_REJECTED -> steps.add(
          "Financing was declined: re-apply for a smaller amount, convert to a cash purchase, or cancel");
      case FINANCING_APPROVED, AWAITING_PAYMENT -> steps.add("Arrange payment with the seller");
      default -> {}
    }
    return steps;
  }

  public PurchasePreviewResponse toPreview(
      Property property, Currency currency, BigDecimal price, List<EligibleOffer> eligible) {
    List<PurchasePreviewResponse.FinancingOption> options = new ArrayList<>();
    for (int i = 0; i < eligible.size(); i++) {
      EligibleOffer e = eligible.get(i);
      options.add(
          PurchasePreviewResponse.FinancingOption.builder()
              .financingOfferId(e.offer().getId())
              .bankId(e.offer().getBankId())
              .bankName(organizationName(e.offer().getBankId()))
              .creditProductId(e.product().getId())
              .creditProductName(e.product().getName())
              .offerLevel(e.level())
              .interestRate(e.interestRate())
              .ltvRatio(e.ltvRatio())
              .minTenureMonths(e.product().getMinTenureMonths())
              .maxTenureMonths(e.product().getMaxTenureMonths())
              .minFinanceableAmount(e.minFinanceable())
              .maxFinanceableAmount(e.maxFinanceable())
              .minimumDownPayment(e.minimumDownPayment(price))
              .partialFinancingAllowed(e.partialFinancingAllowed())
              .recommended(i == 0)
              .build());
    }
    return PurchasePreviewResponse.builder()
        .propertyId(property.getId())
        .listedPrice(price)
        .currency(currency)
        .purchaseType(
            options.isEmpty()
                ? PropertyPurchaseOrder.PurchaseType.CASH
                : PropertyPurchaseOrder.PurchaseType.BANK_FINANCED)
        .financingAvailable(!options.isEmpty())
        .financingOffers(options)
        .build();
  }

  private String organizationName(UUID organizationId) {
    if (organizationId == null) {
      return null;
    }
    return organizationRepository.findById(organizationId).map(o -> o.getName()).orElse(null);
  }

  private String userFullName(UUID userId) {
    if (userId == null) {
      return null;
    }
    return userRepository
        .findById(userId)
        .map(
            u -> {
              String first = u.getFirstName() != null ? u.getFirstName() : "";
              String last = u.getLastName() != null ? u.getLastName() : "";
              String name = (first + " " + last).trim();
              return name.isEmpty() ? null : name;
            })
        .orElse(null);
  }
}
