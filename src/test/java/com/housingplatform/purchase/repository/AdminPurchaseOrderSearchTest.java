package com.housingplatform.purchase.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.housingplatform.BaseIntegrationTest;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder.PurchaseOrderStatus;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder.PurchaseType;
import com.housingplatform.purchase.dto.AdminPurchaseOrderFilter;
import com.housingplatform.purchase.service.PurchaseOrderSpecifications;
import com.housingplatform.shared.domain.Currency;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Runs the admin search specification and the status-count query against the H2 schema, so a wrong
 * attribute name or an invalid JPQL fails here rather than at production start-up.
 */
class AdminPurchaseOrderSearchTest extends BaseIntegrationTest {

  @Autowired private PropertyPurchaseOrderRepository repository;

  private final UUID companyA = UUID.randomUUID();
  private final UUID companyB = UUID.randomUUID();

  @BeforeEach
  void seed() {
    repository.deleteAll();
    repository.save(
        order(
            "PPO-2026-A00001",
            companyA,
            "+251911000001",
            "a@example.com",
            PurchaseType.CASH,
            PurchaseOrderStatus.PENDING_SELLER_REVIEW));
    repository.save(
        order(
            "PPO-2026-A00002",
            companyA,
            "+251911000002",
            null,
            PurchaseType.BANK_FINANCED,
            PurchaseOrderStatus.AWAITING_FINANCING));
    repository.save(
        order(
            "PPO-2026-B00003",
            companyB,
            "+14155551234",
            "diaspora@example.com",
            PurchaseType.CASH,
            PurchaseOrderStatus.COMPLETED));
  }

  @Test
  void noFilterReturnsEverythingNewestFirst() {
    Page<PropertyPurchaseOrder> page =
        repository.findAll(
            PurchaseOrderSpecifications.forAdmin(AdminPurchaseOrderFilter.none()),
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));
    assertThat(page.getTotalElements()).isEqualTo(3);
  }

  @Test
  void filtersCombineWithAnd() {
    AdminPurchaseOrderFilter filter =
        new AdminPurchaseOrderFilter(
            null, PurchaseType.CASH, companyA, null, null, null, null, null);
    List<PropertyPurchaseOrder> rows =
        repository
            .findAll(PurchaseOrderSpecifications.forAdmin(filter), PageRequest.of(0, 10))
            .getContent();
    assertThat(rows)
        .extracting(PropertyPurchaseOrder::getOrderNumber)
        .containsExactly("PPO-2026-A00001");
  }

  @Test
  void textQueryMatchesOrderNumberPhoneOrEmailCaseInsensitively() {
    assertThat(search("b00003")).containsExactly("PPO-2026-B00003");
    assertThat(search("+1415")).containsExactly("PPO-2026-B00003");
    assertThat(search("DIASPORA@")).containsExactly("PPO-2026-B00003");
    assertThat(search("PPO-2026-A"))
        .containsExactlyInAnyOrder("PPO-2026-A00001", "PPO-2026-A00002");
    assertThat(search("nothing-like-this")).isEmpty();
  }

  @Test
  void dateWindowIsInclusiveFromExclusiveTo() {
    LocalDateTime tomorrow = LocalDateTime.now().plusDays(1).toLocalDate().atStartOfDay();
    AdminPurchaseOrderFilter future =
        new AdminPurchaseOrderFilter(null, null, null, null, null, null, tomorrow, null);
    assertThat(repository.count(PurchaseOrderSpecifications.forAdmin(future))).isZero();
    AdminPurchaseOrderFilter untilTomorrow =
        new AdminPurchaseOrderFilter(null, null, null, null, null, null, null, tomorrow);
    assertThat(repository.count(PurchaseOrderSpecifications.forAdmin(untilTomorrow))).isEqualTo(3);
  }

  @Test
  void countByStatusGroupsRows() {
    List<PropertyPurchaseOrderRepository.StatusCount> counts = repository.countByStatus();
    assertThat(counts).hasSize(3);
    assertThat(counts)
        .filteredOn(c -> c.getStatus() == PurchaseOrderStatus.COMPLETED)
        .singleElement()
        .satisfies(c -> assertThat(c.getCount()).isEqualTo(1));
  }

  private List<String> search(String q) {
    AdminPurchaseOrderFilter filter =
        new AdminPurchaseOrderFilter(null, null, null, null, null, q, null, null);
    return repository
        .findAll(PurchaseOrderSpecifications.forAdmin(filter), PageRequest.of(0, 10))
        .map(PropertyPurchaseOrder::getOrderNumber)
        .getContent();
  }

  private static PropertyPurchaseOrder order(
      String number,
      UUID companyId,
      String phone,
      String email,
      PurchaseType type,
      PurchaseOrderStatus status) {
    return PropertyPurchaseOrder.builder()
        .orderNumber(number)
        .propertyId(UUID.randomUUID())
        .buyerId(UUID.randomUUID())
        .realEstateCompanyId(companyId)
        .contactPhone(phone)
        .contactEmail(email)
        .purchaseType(type)
        .status(status)
        .listedPrice(new BigDecimal("8500000.00"))
        .currency(Currency.ETB)
        .build();
  }
}
