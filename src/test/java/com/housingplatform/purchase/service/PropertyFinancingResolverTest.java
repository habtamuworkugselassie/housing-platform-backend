package com.housingplatform.purchase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.housingplatform.banking.domain.CreditProduct;
import com.housingplatform.banking.domain.FinancingOffer;
import com.housingplatform.banking.repository.CreditProductRepository;
import com.housingplatform.banking.repository.FinancingOfferRepository;
import com.housingplatform.property.domain.Building;
import com.housingplatform.property.domain.Property;
import com.housingplatform.purchase.domain.PurchaseOrderFinancing.FinancingMode;
import com.housingplatform.purchase.domain.PurchaseOrderFinancing.OfferLevel;
import com.housingplatform.purchase.service.PropertyFinancingResolver.EligibleOffer;
import com.housingplatform.purchase.service.PropertyFinancingResolver.FinancingChoice;
import com.housingplatform.purchase.service.PropertyFinancingResolver.FinancingResolution;
import com.housingplatform.purchase.service.PropertyFinancingResolver.FinancingTerms;
import com.housingplatform.purchase.service.PropertyFinancingResolver.NotAppliedReason;
import com.housingplatform.shared.domain.Currency;
import com.housingplatform.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * The resolver is the one place that decides "does this property carry an active financing product,
 * and on what terms". Every rule here is a promise made to the buyer on the preview and kept on the
 * order.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PropertyFinancingResolverTest {

  private static final BigDecimal PRICE = new BigDecimal("8500000.00");

  @Mock private FinancingOfferRepository financingOfferRepository;
  @Mock private CreditProductRepository creditProductRepository;
  @InjectMocks private PropertyFinancingResolver resolver;

  private Property property;

  @BeforeEach
  void setUp() {
    property = Property.builder().build();
    property.setId(UUID.randomUUID());
    when(financingOfferRepository.findByPropertyIdAndStatus(any(), any())).thenReturn(List.of());
    when(financingOfferRepository.findByBuildingIdAndStatus(any(), any())).thenReturn(List.of());
  }

  // ------------------------------------------------------------------ fixtures

  private CreditProduct product(String rate, String ltv, String min, String max) {
    CreditProduct p =
        CreditProduct.builder()
            .bankId(UUID.randomUUID())
            .name("Home Purchase Loan")
            .productType(CreditProduct.CreditProductType.HOME_PURCHASE)
            .interestRate(new BigDecimal(rate))
            .minTenureMonths(12)
            .maxTenureMonths(240)
            .maxLoanToValueRatio(new BigDecimal(ltv))
            .minLoanAmount(new BigDecimal(min))
            .maxLoanAmount(new BigDecimal(max))
            .currency(Currency.ETB)
            .status(CreditProduct.CreditProductStatus.ACTIVE)
            .build();
    p.setId(UUID.randomUUID());
    when(creditProductRepository.findById(p.getId())).thenReturn(Optional.of(p));
    return p;
  }

  private FinancingOffer linkToProperty(CreditProduct product) {
    FinancingOffer offer =
        FinancingOffer.builder()
            .bankId(product.getBankId())
            .creditProductId(product.getId())
            .propertyId(property.getId())
            .status(FinancingOffer.FinancingOfferStatus.ACTIVE)
            .build();
    offer.setId(UUID.randomUUID());
    List<FinancingOffer> existing =
        financingOfferRepository.findByPropertyIdAndStatus(
            property.getId(), FinancingOffer.FinancingOfferStatus.ACTIVE);
    List<FinancingOffer> all = new java.util.ArrayList<>(existing);
    all.add(offer);
    when(financingOfferRepository.findByPropertyIdAndStatus(
            eq(property.getId()), eq(FinancingOffer.FinancingOfferStatus.ACTIVE)))
        .thenReturn(all);
    return offer;
  }

  private FinancingOffer linkToBuilding(CreditProduct product) {
    Building building = Building.builder().build();
    building.setId(UUID.randomUUID());
    property.setBuilding(building);
    FinancingOffer offer =
        FinancingOffer.builder()
            .bankId(product.getBankId())
            .creditProductId(product.getId())
            .buildingId(building.getId())
            .status(FinancingOffer.FinancingOfferStatus.ACTIVE)
            .build();
    offer.setId(UUID.randomUUID());
    when(financingOfferRepository.findByBuildingIdAndStatus(
            eq(building.getId()), eq(FinancingOffer.FinancingOfferStatus.ACTIVE)))
        .thenReturn(List.of(offer));
    return offer;
  }

  private FinancingResolution resolve(Boolean useFinancing, FinancingChoice choice) {
    return resolver.resolve(property, Currency.ETB, PRICE, useFinancing, choice);
  }

  // ------------------------------------------------------------------ eligibility

  @Test
  void noLinkedOfferMeansCash() {
    FinancingResolution r = resolve(null, FinancingChoice.none());
    assertThat(r.applied()).isFalse();
    assertThat(r.notAppliedReason()).isEqualTo(NotAppliedReason.NONE_AVAILABLE);
  }

  @Test
  void requiredFinancingWithoutAnOfferIsRejected() {
    assertThatThrownBy(() -> resolve(true, FinancingChoice.none()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("No active financing product");
  }

  @Test
  void buyerCanDeclineFinancingEvenWhenItExists() {
    linkToProperty(product("14.50", "0.80", "500000", "20000000"));
    FinancingResolution r = resolve(false, FinancingChoice.none());
    assertThat(r.applied()).isFalse();
    assertThat(r.notAppliedReason()).isEqualTo(NotAppliedReason.BUYER_DECLINED);
  }

  @Test
  void activeOfferOnInactiveProductIsNotEligible() {
    CreditProduct inactive = product("14.50", "0.80", "500000", "20000000");
    inactive.setStatus(CreditProduct.CreditProductStatus.INACTIVE);
    linkToProperty(inactive);
    assertThat(resolve(null, FinancingChoice.none()).applied()).isFalse();
  }

  @Test
  void nonHomePurchaseProductsAndOtherCurrenciesAreExcluded() {
    CreditProduct material = product("10.00", "0.80", "1000", "20000000");
    material.setProductType(CreditProduct.CreditProductType.MATERIAL_FINANCING);
    linkToProperty(material);
    CreditProduct usd = product("9.00", "0.80", "1000", "20000000");
    usd.setCurrency(Currency.USD);
    linkToProperty(usd);
    assertThat(resolver.listEligible(property, Currency.ETB, PRICE)).isEmpty();
  }

  @Test
  void buildingLevelOfferCountsWhenPropertyHasNone() {
    linkToBuilding(product("14.50", "0.80", "500000", "20000000"));
    FinancingResolution r = resolve(null, FinancingChoice.none());
    assertThat(r.applied()).isTrue();
    assertThat(r.terms().eligible().level()).isEqualTo(OfferLevel.BUILDING);
  }

  @Test
  void lowestEffectiveRateIsRecommendedAndChosenByDefault() {
    linkToProperty(product("16.00", "0.80", "500000", "20000000"));
    CreditProduct cheaper = product("13.25", "0.70", "500000", "20000000");
    FinancingOffer cheaperOffer = linkToProperty(cheaper);

    List<EligibleOffer> eligible = resolver.listEligible(property, Currency.ETB, PRICE);
    assertThat(eligible).hasSize(2);
    assertThat(eligible.get(0).offer().getId()).isEqualTo(cheaperOffer.getId());
    assertThat(resolve(null, FinancingChoice.none()).terms().eligible().product().getId())
        .isEqualTo(cheaper.getId());
  }

  @Test
  void specialOfferRateAndLtvOverrideTheProduct() {
    FinancingOffer offer = linkToProperty(product("16.00", "0.80", "500000", "20000000"));
    offer.setSpecialInterestRate(new BigDecimal("12.00"));
    offer.setSpecialLTVRatio(new BigDecimal("0.90"));
    EligibleOffer e = resolver.listEligible(property, Currency.ETB, PRICE).get(0);
    assertThat(e.interestRate()).isEqualByComparingTo("12.00");
    assertThat(e.ltvRatio()).isEqualByComparingTo("0.90");
    assertThat(e.maxFinanceable()).isEqualByComparingTo("7650000.00");
  }

  @Test
  void explicitOfferMustBeLinkedToThisProperty() {
    linkToProperty(product("14.50", "0.80", "500000", "20000000"));
    FinancingChoice choice = new FinancingChoice(UUID.randomUUID(), null, null, null);
    assertThatThrownBy(() -> resolve(null, choice))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("not an active financing product linked to this property");
  }

  @Test
  void productMaxLoanCapsTheFinanceableAmount() {
    linkToProperty(product("14.50", "0.80", "500000", "3000000"));
    EligibleOffer e = resolver.listEligible(property, Currency.ETB, PRICE).get(0);
    assertThat(e.maxFinanceable()).isEqualByComparingTo("3000000.00");
    assertThat(e.minimumDownPayment(PRICE)).isEqualByComparingTo("5500000.00");
  }

  // ------------------------------------------------------------------ terms & partial financing

  @Test
  void defaultIsMaximumModeAtMaxTenure() {
    linkToProperty(product("14.50", "0.80", "500000", "20000000"));
    FinancingTerms t = resolve(null, FinancingChoice.none()).terms();
    assertThat(t.mode()).isEqualTo(FinancingMode.MAXIMUM);
    assertThat(t.financedAmount()).isEqualByComparingTo("6800000.00");
    assertThat(t.cashPortion()).isEqualByComparingTo("1700000.00");
    assertThat(t.coverageRatio()).isEqualByComparingTo("0.8000");
    assertThat(t.tenureMonths()).isEqualTo(240);
    assertThat(t.estimatedMonthlyInstallment()).isEqualByComparingTo("87039.85");
  }

  @Test
  void partialFinancingByFinancedAmount() {
    linkToProperty(product("14.50", "0.80", "500000", "20000000"));
    FinancingChoice choice = new FinancingChoice(null, new BigDecimal("4250000"), null, 180);
    FinancingTerms t = resolve(null, choice).terms();
    assertThat(t.mode()).isEqualTo(FinancingMode.PARTIAL);
    assertThat(t.financedAmount()).isEqualByComparingTo("4250000.00");
    assertThat(t.cashPortion()).isEqualByComparingTo("4250000.00");
    assertThat(t.coverageRatio()).isEqualByComparingTo("0.5000");
    assertThat(t.tenureMonths()).isEqualTo(180);
    assertThat(t.estimatedMonthlyInstallment()).isEqualByComparingTo("58033.79");
  }

  @Test
  void partialFinancingByDownPayment() {
    linkToProperty(product("14.50", "0.80", "500000", "20000000"));
    FinancingChoice choice = new FinancingChoice(null, null, new BigDecimal("2000000"), null);
    FinancingTerms t = resolve(null, choice).terms();
    assertThat(t.financedAmount()).isEqualByComparingTo("6500000.00");
    assertThat(t.cashPortion()).isEqualByComparingTo("2000000.00");
    assertThat(t.mode()).isEqualTo(FinancingMode.PARTIAL);
  }

  @Test
  void financedAmountAndDownPaymentAreMutuallyExclusive() {
    linkToProperty(product("14.50", "0.80", "500000", "20000000"));
    FinancingChoice choice =
        new FinancingChoice(null, new BigDecimal("4000000"), new BigDecimal("4500000"), null);
    assertThatThrownBy(() -> resolve(null, choice))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("not both");
  }

  @Test
  void financedAmountBelowProductMinimumIsRejectedWithTheRange() {
    linkToProperty(product("14.50", "0.80", "500000", "20000000"));
    FinancingChoice choice = new FinancingChoice(null, new BigDecimal("300000"), null, null);
    assertThatThrownBy(() -> resolve(null, choice))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("minimum 500000.00")
        .hasMessageContaining("maximum 6800000.00");
  }

  @Test
  void financedAmountAboveLtvCapIsRejected() {
    linkToProperty(product("14.50", "0.80", "500000", "20000000"));
    FinancingChoice choice = new FinancingChoice(null, new BigDecimal("7000000"), null, null);
    assertThatThrownBy(() -> resolve(null, choice)).isInstanceOf(BusinessException.class);
  }

  @Test
  void tenureOutsideProductRangeIsRejected() {
    linkToProperty(product("14.50", "0.80", "500000", "20000000"));
    assertThatThrownBy(() -> resolve(null, new FinancingChoice(null, null, null, 6)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("12-240");
    assertThatThrownBy(() -> resolve(null, new FinancingChoice(null, null, null, 300)))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void partialFinancingIsOnlyAllowedWhenTheRangeIsWiderThanOneValue() {
    linkToProperty(product("14.50", "0.80", "6800000", "6800000"));
    EligibleOffer e = resolver.listEligible(property, Currency.ETB, PRICE).get(0);
    assertThat(e.partialFinancingAllowed()).isFalse();
  }

  @Test
  void zeroRateProductDegeneratesToStraightLine() {
    assertThat(
            PropertyFinancingResolver.monthlyInstallment(
                new BigDecimal("1200000"), BigDecimal.ZERO, 120))
        .isEqualByComparingTo("10000.00");
  }
}
