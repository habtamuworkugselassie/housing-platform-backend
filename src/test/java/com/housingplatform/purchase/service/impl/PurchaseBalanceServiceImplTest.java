package com.housingplatform.purchase.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.media.service.MediaStorageService;
import com.housingplatform.payment.chapa.ChapaClient;
import com.housingplatform.payment.chapa.ChapaClient.InitializeRequest;
import com.housingplatform.payment.chapa.ChapaClient.InitializeResult;
import com.housingplatform.payment.chapa.ChapaClient.VerifyResult;
import com.housingplatform.payment.chapa.ChapaPaymentMethod;
import com.housingplatform.payment.chapa.ChapaProperties;
import com.housingplatform.purchase.config.PurchaseProviderProperties;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder.PurchaseOrderStatus;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder.PurchaseType;
import com.housingplatform.purchase.domain.PurchaseBalanceInstalment;
import com.housingplatform.purchase.domain.PurchaseBalancePayment;
import com.housingplatform.purchase.domain.PurchaseBalancePayment.Status;
import com.housingplatform.purchase.domain.PurchaseDeposit;
import com.housingplatform.purchase.domain.PurchaseDeposit.DepositStatus;
import com.housingplatform.purchase.repository.PropertyPurchaseOrderRepository;
import com.housingplatform.purchase.repository.PurchaseBalanceInstalmentRepository;
import com.housingplatform.purchase.repository.PurchaseBalancePaymentRepository;
import com.housingplatform.purchase.service.ProviderBankAccountSettings;
import com.housingplatform.purchase.service.PurchaseBalanceService.InstalmentLine;
import com.housingplatform.purchase.service.PurchaseOrderActor;
import com.housingplatform.shared.domain.Currency;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class PurchaseBalanceServiceImplTest {

  private final PropertyPurchaseOrderRepository orderRepository =
      mock(PropertyPurchaseOrderRepository.class);
  private final PurchaseBalanceInstalmentRepository instalmentRepository =
      mock(PurchaseBalanceInstalmentRepository.class);
  private final PurchaseBalancePaymentRepository paymentRepository =
      mock(PurchaseBalancePaymentRepository.class);
  private final ChapaClient chapa = mock(ChapaClient.class);
  private final MediaStorageService storage = mock(MediaStorageService.class);
  private final ProviderBankAccountSettings bank = mock(ProviderBankAccountSettings.class);

  private final List<PurchaseBalancePayment> payments = new ArrayList<>();
  private final List<PurchaseBalanceInstalment> instalments = new ArrayList<>();
  private final UUID buyerId = UUID.randomUUID();
  private final UUID companyId = UUID.randomUUID();
  private final PurchaseOrderActor buyer = PurchaseOrderActor.buyer(buyerId);
  private final PurchaseOrderActor seller =
      new PurchaseOrderActor(UUID.randomUUID(), companyId, null, false);
  private final PurchaseOrderActor stranger = PurchaseOrderActor.buyer(UUID.randomUUID());

  private PurchaseBalanceServiceImpl service;
  private PropertyPurchaseOrder order;

  @BeforeEach
  void setUp() {
    service =
        new PurchaseBalanceServiceImpl(
            orderRepository,
            instalmentRepository,
            paymentRepository,
            mock(UserRepository.class),
            chapa,
            new ChapaProperties(),
            new PurchaseProviderProperties(),
            bank,
            storage);
    order =
        PropertyPurchaseOrder.builder()
            .orderNumber("PPO-2026-1F99D681")
            .propertyId(UUID.randomUUID())
            .buyerId(buyerId)
            .realEstateCompanyId(companyId)
            .contactPhone("+251920783801")
            .purchaseType(PurchaseType.CASH)
            .status(PurchaseOrderStatus.AWAITING_PAYMENT)
            .listedPrice(new BigDecimal("3200000.00"))
            .currency(Currency.ETB)
            .build();
    order.setId(UUID.randomUUID());
    order.setDeposit(
        PurchaseDeposit.builder()
            .purchaseOrder(order)
            .amount(new BigDecimal("32000.00"))
            .currency(Currency.ETB)
            .status(DepositStatus.PAID)
            .build());
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
    when(chapa.isConfigured()).thenReturn(true);
    when(paymentRepository.findByPurchaseOrderIdOrderByCreatedAtAsc(order.getId()))
        .thenAnswer(inv -> List.copyOf(payments));
    when(paymentRepository.save(any()))
        .thenAnswer(
            inv -> {
              PurchaseBalancePayment p = inv.getArgument(0);
              if (p.getId() == null) {
                p.setId(UUID.randomUUID());
                payments.add(p);
              }
              return p;
            });
    when(paymentRepository.findByIdAndPurchaseOrderId(any(), any()))
        .thenAnswer(
            inv -> payments.stream().filter(p -> p.getId().equals(inv.getArgument(0))).findFirst());
    when(paymentRepository.findByTxRef(anyString()))
        .thenAnswer(
            inv ->
                payments.stream().filter(p -> inv.getArgument(0).equals(p.getTxRef())).findFirst());
    when(instalmentRepository.findByPurchaseOrderIdOrderBySequenceAsc(order.getId()))
        .thenAnswer(inv -> List.copyOf(instalments));
    when(instalmentRepository.save(any()))
        .thenAnswer(
            inv -> {
              instalments.add(inv.getArgument(0));
              return inv.getArgument(0);
            });
    org.mockito.Mockito.doAnswer(
            inv -> {
              instalments.clear();
              return null;
            })
        .when(instalmentRepository)
        .deleteByPurchaseOrderId(any());
    when(chapa.initialize(any())).thenReturn(new InitializeResult("https://checkout/x"));
    when(storage.save(any(), anyString()))
        .thenReturn("/api/v1/uploads/private/balance-slips/x.pdf");
  }

  private static VerifyResult success(String amount) {
    return new VerifyResult(
        "success", new BigDecimal(amount), "ETB", "CH1", null, "telebirr", null, null);
  }

  @Test
  void theBalanceIsThePriceLessTheDepositAndTheLoan() {
    var r = service.get(buyer, order.getId());
    assertThat(r.getBalanceDue()).isEqualByComparingTo("3168000.00");
    assertThat(r.getRemaining()).isEqualByComparingTo("3168000.00");
    assertThat(r.isPayable()).isTrue();
    assertThat(r.getInstalments())
        .singleElement()
        .extracting(i -> i.getLabel())
        .isEqualTo("Balance");
    assertThat(r.getTransferReference()).isEqualTo("PPO-2026-1F99D681-BAL");

    order.setPurchaseType(PurchaseType.BANK_FINANCED);
    order.setFinancing(
        com.housingplatform.purchase.domain.PurchaseOrderFinancing.builder()
            .financedAmount(new BigDecimal("2000000.00"))
            .approvedAmount(new BigDecimal("1800000.00"))
            .build());
    assertThat(service.get(buyer, order.getId()).getBalanceDue())
        .isEqualByComparingTo("1368000.00");

    order.getDeposit().setStatus(DepositStatus.WAIVED);
    assertThat(service.get(buyer, order.getId()).getDepositCredit()).isZero();
  }

  @Test
  void aUsdDepositCountsAtItsBirrValue() {
    PurchaseDeposit d = order.getDeposit();
    d.setAmount(new BigDecimal("213.33"));
    d.setCurrency(Currency.USD);
    d.setBaseAmount(new BigDecimal("32000.00"));
    d.setBaseCurrency(Currency.ETB);
    assertThat(PurchaseBalanceServiceImpl.depositCredit(order)).isEqualByComparingTo("32000.00");
  }

  @Test
  void theBuyerPaysOnlineInPartsWithinTheRemainingBalance() {
    var checkout =
        service.startOnline(
            buyer, order.getId(), new BigDecimal("150000"), ChapaPaymentMethod.TELEBIRR);
    ArgumentCaptor<InitializeRequest> req = ArgumentCaptor.forClass(InitializeRequest.class);
    verify(chapa).initialize(req.capture());
    assertThat(req.getValue().amount()).isEqualByComparingTo("150000.00");
    assertThat(req.getValue().preferredPaymentMethods()).containsExactly("telebirr");
    assertThat(req.getValue().txRef()).startsWith("1F99D681-B1-").hasSizeLessThanOrEqualTo(20);
    assertThat(req.getValue().returnUrl()).endsWith("?balance=return");
    assertThat(checkout.getCheckoutUrl()).isEqualTo("https://checkout/x");

    when(chapa.verify(checkout.getTxRef())).thenReturn(success("150000.00"));
    service.handleProviderNotification(checkout.getTxRef());
    var r = service.get(buyer, order.getId());
    assertThat(r.getPaid()).isEqualByComparingTo("150000.00");
    assertThat(r.getRemaining()).isEqualByComparingTo("3018000.00");
    assertThat(service.blocksCompletion(order)).isTrue();

    assertThatThrownBy(
            () -> service.startOnline(buyer, order.getId(), new BigDecimal("3018000.01"), null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("At most 3018000.00");
  }

  @Test
  void anUnderpaidCheckoutFails() {
    var checkout = service.startOnline(buyer, order.getId(), new BigDecimal("50000"), null);
    when(chapa.verify(checkout.getTxRef())).thenReturn(success("40000.00"));
    service.confirmOnline(buyer, order.getId());
    assertThat(payments.get(0).getStatus()).isEqualTo(Status.FAILED);
    assertThat(service.get(buyer, order.getId()).getPaid()).isZero();
  }

  @Test
  void theBalanceCannotBePaidBeforeTheSellerAccepts() {
    order.setStatus(PurchaseOrderStatus.PENDING_SELLER_REVIEW);
    assertThatThrownBy(() -> service.startOnline(buyer, order.getId(), BigDecimal.TEN, null))
        .hasMessageContaining("once the seller accepts");
    assertThatThrownBy(() -> service.startOnline(stranger, order.getId(), BigDecimal.TEN, null))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void aBankTransferCountsOnceTheSellerConfirmsIt() {
    var slip = new MockMultipartFile("slip", "receipt.pdf", "application/pdf", "%PDF".getBytes());
    var r =
        service.reportTransfer(
            buyer, order.getId(), new BigDecimal("3168000"), "FT2609251234", LocalDate.now(), slip);
    assertThat(r.getInProgress()).isEqualByComparingTo("3168000.00");
    assertThat(r.getPaid()).isZero();
    UUID paymentId = payments.get(0).getId();

    assertThatThrownBy(() -> service.reviewTransfer(buyer, order.getId(), paymentId, true, null))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> service.reviewTransfer(seller, order.getId(), paymentId, false, " "))
        .hasMessageContaining("why");

    var after = service.reviewTransfer(seller, order.getId(), paymentId, true, null);
    assertThat(after.isFullyPaid()).isTrue();
    assertThat(service.blocksCompletion(order)).isFalse();
  }

  @Test
  void theScheduleMustAddUpAndLocksOncePaid() {
    assertThatThrownBy(
            () ->
                service.setSchedule(
                    seller,
                    order.getId(),
                    List.of(new InstalmentLine("First", new BigDecimal("1000000"), null))))
        .hasMessageContaining("add up to 1000000.00");

    var r =
        service.setSchedule(
            seller,
            order.getId(),
            List.of(
                new InstalmentLine(
                    "On signing", new BigDecimal("1168000"), LocalDate.now().minusDays(1)),
                new InstalmentLine(
                    null, new BigDecimal("2000000"), LocalDate.now().plusMonths(3))));
    assertThat(r.getInstalments()).hasSize(2);
    assertThat(r.getInstalments().get(0).isOverdue()).isTrue();
    assertThat(r.getInstalments().get(1).getLabel()).isEqualTo("Instalment 2");

    service.record(
        seller, order.getId(), new BigDecimal("1168000"), "CHQ-77", LocalDate.now(), null);
    var paid = service.get(seller, order.getId());
    assertThat(paid.getInstalments().get(0).isPaid()).isTrue();
    assertThat(paid.getInstalments().get(1).isPaid()).isFalse();
    assertThatThrownBy(
            () ->
                service.setSchedule(
                    seller,
                    order.getId(),
                    List.of(new InstalmentLine("All", new BigDecimal("3168000"), null))))
        .hasMessageContaining("already been made");
  }

  @Test
  void closingTheOrderCancelsPaymentsInFlightAndFlagsPaidOnesForRefund() {
    service.record(seller, order.getId(), new BigDecimal("100000"), "CHQ-1", null, null);
    service.startOnline(buyer, order.getId(), new BigDecimal("50000"), null);
    service.onOrderClosed(order);
    assertThat(payments)
        .extracting(PurchaseBalancePayment::getStatus)
        .containsExactly(Status.REFUND_PENDING, Status.CANCELLED);
  }

  @Test
  void usdOrdersPayByCardOnly() {
    order.setCurrency(Currency.USD);
    order.setListedPrice(new BigDecimal("50000.00"));
    order.getDeposit().setAmount(new BigDecimal("500.00"));
    order.getDeposit().setCurrency(Currency.USD);
    service.startOnline(buyer, order.getId(), new BigDecimal("1000"), ChapaPaymentMethod.TELEBIRR);
    assertThat(payments.get(0).getPreferredMethod()).isEqualTo(ChapaPaymentMethod.CARD);
  }
}
