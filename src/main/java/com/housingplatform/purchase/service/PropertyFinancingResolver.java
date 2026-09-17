package com.housingplatform.purchase.service;

import com.housingplatform.banking.domain.CreditProduct;
import com.housingplatform.banking.domain.FinancingOffer;
import com.housingplatform.banking.repository.CreditProductRepository;
import com.housingplatform.banking.repository.FinancingOfferRepository;
import com.housingplatform.property.domain.Property;
import com.housingplatform.purchase.domain.PurchaseOrderFinancing.FinancingMode;
import com.housingplatform.purchase.domain.PurchaseOrderFinancing.OfferLevel;
import com.housingplatform.shared.domain.Currency;
import com.housingplatform.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Looks up the property's product catalog and decides whether, and on which terms, a purchase order
 * is bank financed. Shared by the preview and the create paths so the two can never drift.
 *
 * <p>It never does credit scoring: that is the bank's job through the loan application. It only
 * guarantees that an order carries a currently active product with internally consistent numbers.
 */
@Component
@RequiredArgsConstructor
public class PropertyFinancingResolver {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

  private final FinancingOfferRepository financingOfferRepository;
  private final CreditProductRepository creditProductRepository;

  /** An ACTIVE offer whose ACTIVE product can actually lend against this price. */
  public record EligibleOffer(
      FinancingOffer offer,
      CreditProduct product,
      OfferLevel level,
      BigDecimal interestRate,
      BigDecimal ltvRatio,
      BigDecimal minFinanceable,
      BigDecimal maxFinanceable) {

    public BigDecimal minimumDownPayment(BigDecimal listedPrice) {
      return listedPrice.subtract(maxFinanceable);
    }

    public boolean partialFinancingAllowed() {
      return maxFinanceable.compareTo(minFinanceable) > 0;
    }
  }

  /** The concrete split and schedule for one eligible offer. */
  public record FinancingTerms(
      EligibleOffer eligible,
      FinancingMode mode,
      BigDecimal financedAmount,
      BigDecimal cashPortion,
      BigDecimal coverageRatio,
      int tenureMonths,
      BigDecimal estimatedMonthlyInstallment) {}

  /** Buyer input that influences the split. All fields optional. */
  public record FinancingChoice(
      UUID financingOfferId,
      BigDecimal financedAmount,
      BigDecimal downPaymentAmount,
      Integer requestedTenureMonths) {
    public static FinancingChoice none() {
      return new FinancingChoice(null, null, null, null);
    }
  }

  public enum NotAppliedReason {
    NONE_AVAILABLE,
    BUYER_DECLINED
  }

  /** Outcome of {@link #resolve}: either terms to embed, or the reason the order stays cash. */
  public record FinancingResolution(FinancingTerms terms, NotAppliedReason notAppliedReason) {
    public boolean applied() {
      return terms != null;
    }

    static FinancingResolution notApplied(NotAppliedReason reason) {
      return new FinancingResolution(null, reason);
    }
  }

  /** Every eligible offer for the property, best (recommended) first. */
  public List<EligibleOffer> listEligible(Property property, Currency currency, BigDecimal price) {
    Map<UUID, OfferLevel> candidates = new LinkedHashMap<>();
    List<FinancingOffer> offers = new ArrayList<>();
    for (FinancingOffer offer :
        financingOfferRepository.findByPropertyIdAndStatus(
            property.getId(), FinancingOffer.FinancingOfferStatus.ACTIVE)) {
      candidates.put(offer.getId(), OfferLevel.PROPERTY);
      offers.add(offer);
    }
    if (property.getBuilding() != null) {
      for (FinancingOffer offer :
          financingOfferRepository.findByBuildingIdAndStatus(
              property.getBuilding().getId(), FinancingOffer.FinancingOfferStatus.ACTIVE)) {
        if (candidates.putIfAbsent(offer.getId(), OfferLevel.BUILDING) == null) {
          offers.add(offer);
        }
      }
    }

    List<EligibleOffer> eligible = new ArrayList<>();
    for (FinancingOffer offer : offers) {
      toEligible(offer, candidates.get(offer.getId()), currency, price).ifPresent(eligible::add);
    }
    eligible.sort(
        Comparator.comparing(EligibleOffer::interestRate)
            .thenComparing(EligibleOffer::ltvRatio, Comparator.reverseOrder())
            .thenComparing(e -> e.level() == OfferLevel.PROPERTY ? 0 : 1));
    return eligible;
  }

  /**
   * Decides the financing leg of a new order.
   *
   * @param useFinancing null = automatic, false = cash even if financing exists, true = required
   */
  public FinancingResolution resolve(
      Property property,
      Currency currency,
      BigDecimal price,
      Boolean useFinancing,
      FinancingChoice choice) {
    if (Boolean.FALSE.equals(useFinancing)) {
      return FinancingResolution.notApplied(NotAppliedReason.BUYER_DECLINED);
    }
    List<EligibleOffer> eligible = listEligible(property, currency, price);
    if (eligible.isEmpty()) {
      if (Boolean.TRUE.equals(useFinancing)) {
        throw new BusinessException(
            "No active financing product is linked to this property; financing cannot be applied");
      }
      return FinancingResolution.notApplied(NotAppliedReason.NONE_AVAILABLE);
    }
    FinancingChoice effective = choice != null ? choice : FinancingChoice.none();
    EligibleOffer chosen = pick(eligible, effective.financingOfferId());
    return new FinancingResolution(computeTerms(chosen, price, effective), null);
  }

  /**
   * Re-computes terms for an offer the order already carries (used when the buyer changes the
   * split).
   */
  public FinancingTerms recompute(
      Property property,
      Currency currency,
      BigDecimal price,
      UUID financingOfferId,
      FinancingChoice choice) {
    List<EligibleOffer> eligible = listEligible(property, currency, price);
    if (eligible.isEmpty()) {
      throw new BusinessException(
          "The financing product linked to this property is no longer active");
    }
    return computeTerms(pick(eligible, financingOfferId), price, choice);
  }

  private EligibleOffer pick(List<EligibleOffer> eligible, UUID requestedOfferId) {
    if (requestedOfferId == null) {
      return eligible.get(0);
    }
    return eligible.stream()
        .filter(e -> e.offer().getId().equals(requestedOfferId))
        .findFirst()
        .orElseThrow(
            () ->
                new BusinessException(
                    "Financing offer "
                        + requestedOfferId
                        + " is not an active financing product linked to this property"));
  }

  FinancingTerms computeTerms(EligibleOffer e, BigDecimal price, FinancingChoice choice) {
    if (choice.financedAmount() != null && choice.downPaymentAmount() != null) {
      throw new BusinessException("Provide either financedAmount or downPaymentAmount, not both");
    }
    BigDecimal financed;
    if (choice.financedAmount() != null) {
      financed = scale2(choice.financedAmount());
    } else if (choice.downPaymentAmount() != null) {
      financed = scale2(price.subtract(choice.downPaymentAmount()));
    } else {
      financed = e.maxFinanceable();
    }
    if (financed.compareTo(e.minFinanceable()) < 0 || financed.compareTo(e.maxFinanceable()) > 0) {
      throw new BusinessException(
          String.format(
              "Financed amount %s is outside the allowed range for this product: minimum %s, maximum %s",
              financed.toPlainString(),
              e.minFinanceable().toPlainString(),
              e.maxFinanceable().toPlainString()));
    }

    int tenure =
        choice.requestedTenureMonths() != null
            ? choice.requestedTenureMonths()
            : e.product().getMaxTenureMonths();
    if (tenure < e.product().getMinTenureMonths() || tenure > e.product().getMaxTenureMonths()) {
      throw new BusinessException(
          String.format(
              "Requested tenure %d months is outside the product range %d-%d months",
              tenure, e.product().getMinTenureMonths(), e.product().getMaxTenureMonths()));
    }

    BigDecimal cash = price.subtract(financed);
    FinancingMode mode =
        financed.compareTo(e.maxFinanceable()) == 0 ? FinancingMode.MAXIMUM : FinancingMode.PARTIAL;
    BigDecimal coverage = financed.divide(price, 4, RoundingMode.HALF_UP);
    return new FinancingTerms(
        e,
        mode,
        financed,
        cash,
        coverage,
        tenure,
        monthlyInstallment(financed, e.interestRate(), tenure));
  }

  private Optional<EligibleOffer> toEligible(
      FinancingOffer offer, OfferLevel level, Currency currency, BigDecimal price) {
    Optional<CreditProduct> maybeProduct =
        creditProductRepository.findById(offer.getCreditProductId());
    if (maybeProduct.isEmpty()) {
      return Optional.empty();
    }
    CreditProduct product = maybeProduct.get();
    if (product.getStatus() != CreditProduct.CreditProductStatus.ACTIVE
        || product.getProductType() != CreditProduct.CreditProductType.HOME_PURCHASE
        || product.getCurrency() != currency) {
      return Optional.empty();
    }
    BigDecimal ltv =
        offer.getSpecialLTVRatio() != null
            ? offer.getSpecialLTVRatio()
            : product.getMaxLoanToValueRatio();
    BigDecimal rate =
        offer.getSpecialInterestRate() != null
            ? offer.getSpecialInterestRate()
            : product.getInterestRate();
    if (ltv == null || rate == null || ltv.signum() <= 0) {
      return Optional.empty();
    }
    BigDecimal maxFinanceable = scale2(price.multiply(ltv));
    if (product.getMaxLoanAmount() != null
        && product.getMaxLoanAmount().compareTo(maxFinanceable) < 0) {
      maxFinanceable = scale2(product.getMaxLoanAmount());
    }
    BigDecimal minFinanceable =
        product.getMinLoanAmount() != null ? scale2(product.getMinLoanAmount()) : BigDecimal.ZERO;
    if (minFinanceable.signum() <= 0) {
      minFinanceable = new BigDecimal("0.01");
    }
    if (maxFinanceable.compareTo(minFinanceable) < 0) {
      return Optional.empty();
    }
    return Optional.of(
        new EligibleOffer(offer, product, level, rate, ltv, minFinanceable, maxFinanceable));
  }

  /** Standard amortised instalment: P·i / (1 − (1+i)^−n); a zero rate degenerates to P / n. */
  public static BigDecimal monthlyInstallment(
      BigDecimal principal, BigDecimal annualRatePercent, int months) {
    if (months <= 0) {
      return null;
    }
    if (annualRatePercent == null || annualRatePercent.signum() == 0) {
      return principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
    }
    MathContext mc = new MathContext(20, RoundingMode.HALF_EVEN);
    BigDecimal i = annualRatePercent.divide(HUNDRED, mc).divide(TWELVE, mc);
    BigDecimal factor = BigDecimal.ONE.add(i).pow(months, mc);
    BigDecimal denominator = BigDecimal.ONE.subtract(BigDecimal.ONE.divide(factor, mc));
    return principal.multiply(i, mc).divide(denominator, 2, RoundingMode.HALF_UP);
  }

  private static BigDecimal scale2(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }
}
