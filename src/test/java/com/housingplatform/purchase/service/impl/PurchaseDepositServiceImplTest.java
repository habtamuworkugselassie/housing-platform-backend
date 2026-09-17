package com.housingplatform.purchase.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.payment.chapa.ChapaClient;
import com.housingplatform.payment.chapa.ChapaClient.InitializeRequest;
import com.housingplatform.payment.chapa.ChapaClient.InitializeResult;
import com.housingplatform.payment.chapa.ChapaClient.VerifyResult;
import com.housingplatform.payment.chapa.ChapaProperties;
import com.housingplatform.purchase.config.PurchaseDepositProperties;
import com.housingplatform.purchase.config.PurchaseProviderProperties;
import com.housingplatform.purchase.domain.AgreementTemplate.AgreementType;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder.PurchaseOrderStatus;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder.PurchaseType;
import com.housingplatform.purchase.domain.PurchaseAgreement;
import com.housingplatform.purchase.domain.PurchaseAgreement.AgreementStatus;
import com.housingplatform.purchase.domain.PurchaseDeposit;
import com.housingplatform.purchase.domain.PurchaseDeposit.DepositStatus;
import com.housingplatform.purchase.dto.DepositCheckoutResponse;
import com.housingplatform.purchase.repository.PropertyPurchaseOrderRepository;
import com.housingplatform.purchase.repository.PurchaseDepositRepository;
import com.housingplatform.purchase.service.DepositPolicy;
import com.housingplatform.purchase.service.PurchaseOrderActor;
import com.housingplatform.purchase.service.PurchaseOrderEvents.PurchaseDepositPaidEvent;
import com.housingplatform.shared.domain.Currency;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseDepositServiceImplTest {

  @Mock private PurchaseDepositRepository depositRepository;
  @Mock private PropertyPurchaseOrderRepository orderRepository;
  @Mock private UserRepository userRepository;
  @Mock private ChapaClient chapa;
  @Mock private ApplicationEventPublisher eventPublisher;

  private PurchaseDepositServiceImpl service;
  private final UUID buyerId = UUID.randomUUID();
  private final PurchaseOrderActor buyer = PurchaseOrderActor.buyer(buyerId);
  private PropertyPurchaseOrder order;

  @BeforeEach
  void setUp() {
    service =
        new PurchaseDepositServiceImpl(
            depositRepository,
            orderRepository,
            userRepository,
            new DepositPolicy(new PurchaseDepositProperties()),
            chapa,
            new ChapaProperties(),
            new PurchaseProviderProperties(),
            eventPublisher);
    when(chapa.isConfigured()).thenReturn(true);
    when(depositRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    order =
        PropertyPurchaseOrder.builder()
            .orderNumber("PPO-2026-TEST0001")
            .propertyId(UUID.randomUUID())
            .buyerId(buyerId)
            .contactPhone("+251911223344")
            .contactEmail("abebe@example.com")
            .purchaseType(PurchaseType.CASH)
            .status(PurchaseOrderStatus.AWAITING_PAYMENT)
            .listedPrice(new BigDecimal("8500000.00"))
            .currency(Currency.ETB)
            .build();
    order.setId(UUID.randomUUID());
    when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
  }

  private PurchaseDeposit issued() {
    service.issueForOrder(order);
    return order.getDeposit();
  }

  private void addSignedTerms(AgreementStatus status) {
    PurchaseAgreement a =
        PurchaseAgreement.builder()
            .purchaseOrder(order)
            .templateId(UUID.randomUUID())
            .type(AgreementType.RESERVATION_DEPOSIT_TERMS)
            .templateVersion(1)
            .sequence(2)
            .title("Reservation Deposit Terms")
            .content("x")
            .contentHash("h")
            .status(status)
            .blocksCompletion(true)
            .issuedAt(LocalDateTime.now())
            .buyerUserId(buyerId)
            .providerName("Dream Team PLC")
            .build();
    order.getAgreements().add(a);
  }

  // ------------------------------------------------------------------ issue / gates

  @Test
  void issuesADueDepositFromThePolicy() {
    PurchaseDeposit d = issued();
    assertThat(d.getStatus()).isEqualTo(DepositStatus.DUE);
    assertThat(d.getAmount()).isEqualByComparingTo("85000.00");
    assertThat(d.getCurrency()).isEqualTo(Currency.ETB);
    assertThat(d.getDueAt()).isAfter(LocalDateTime.now().plusDays(2));
    assertThat(service.blocksCompletion(order)).isTrue();
    service.issueForOrder(order);
    assertThat(order.getDeposit()).isSameAs(d); // idempotent
  }

  @Test
  void disabledPolicyIssuesNothing() {
    PurchaseDepositProperties props = new PurchaseDepositProperties();
    props.setEnabled(false);
    PurchaseDepositServiceImpl disabled =
        new PurchaseDepositServiceImpl(
            depositRepository,
            orderRepository,
            userRepository,
            new DepositPolicy(props),
            chapa,
            new ChapaProperties(),
            new PurchaseProviderProperties(),
            eventPublisher);
    disabled.issueForOrder(order);
    assertThat(order.getDeposit()).isNull();
    assertThat(disabled.blocksCompletion(order)).isFalse();
  }

  // ------------------------------------------------------------------ checkout

  @Test
  void checkoutRequiresSignedDepositTermsAndAConfiguredProvider() {
    issued();
    addSignedTerms(AgreementStatus.PENDING_BUYER_SIGNATURE);
    assertThatThrownBy(() -> service.startCheckout(buyer, order.getId()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("sign the reservation deposit terms");

    order.getAgreements().clear();
    when(chapa.isConfigured()).thenReturn(false);
    assertThatThrownBy(() -> service.startCheckout(buyer, order.getId()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("not configured");
  }

  @Test
  void checkoutInitialisesAHostedSessionWithTheOrderDetails() {
    issued();
    addSignedTerms(AgreementStatus.FULLY_SIGNED);
    when(chapa.initialize(any())).thenReturn(new InitializeResult("https://checkout.chapa.co/x"));

    DepositCheckoutResponse r = service.startCheckout(buyer, order.getId());

    ArgumentCaptor<InitializeRequest> req = ArgumentCaptor.forClass(InitializeRequest.class);
    verify(chapa).initialize(req.capture());
    assertThat(req.getValue().amount()).isEqualByComparingTo("85000.00");
    assertThat(req.getValue().currency()).isEqualTo("ETB");
    assertThat(req.getValue().email()).isEqualTo("abebe@example.com");
    assertThat(req.getValue().phoneNumber()).isEqualTo("+251911223344");
    assertThat(req.getValue().txRef()).startsWith("PPO-2026-TEST0001-DEP1-");
    assertThat(req.getValue().returnUrl())
        .contains("/purchase-orders/" + order.getId() + "?deposit=return");
    assertThat(r.getCheckoutUrl()).isEqualTo("https://checkout.chapa.co/x");
    PurchaseDeposit d = order.getDeposit();
    assertThat(d.getStatus()).isEqualTo(DepositStatus.PENDING);
    assertThat(d.getAttempts()).isEqualTo(1);
    assertThat(d.getTxRef()).isEqualTo(r.getTxRef());
  }

  @Test
  void aPendingCheckoutIsResumedNotDuplicated() {
    PurchaseDeposit d = issued();
    d.setStatus(DepositStatus.PENDING);
    d.setTxRef("PPO-2026-TEST0001-DEP1-AAAAAA");
    d.setCheckoutUrl("https://checkout.chapa.co/old");
    d.setAttempts(1);
    when(chapa.verify(d.getTxRef()))
        .thenReturn(new VerifyResult("pending", null, null, null, d.getTxRef(), null, null, null));

    DepositCheckoutResponse r = service.startCheckout(buyer, order.getId());
    assertThat(r.getCheckoutUrl()).isEqualTo("https://checkout.chapa.co/old");
    verify(chapa, never()).initialize(any());
  }

  @Test
  void aFailedPendingCheckoutIsRestartedWithANewReference() {
    PurchaseDeposit d = issued();
    d.setStatus(DepositStatus.PENDING);
    d.setTxRef("PPO-2026-TEST0001-DEP1-AAAAAA");
    d.setCheckoutUrl("https://checkout.chapa.co/old");
    d.setAttempts(1);
    when(chapa.verify(d.getTxRef()))
        .thenReturn(new VerifyResult("failed", null, null, null, d.getTxRef(), null, null, null));
    when(chapa.initialize(any())).thenReturn(new InitializeResult("https://checkout.chapa.co/new"));

    DepositCheckoutResponse r = service.startCheckout(buyer, order.getId());
    assertThat(r.getCheckoutUrl()).isEqualTo("https://checkout.chapa.co/new");
    assertThat(d.getTxRef()).startsWith("PPO-2026-TEST0001-DEP2-");
    assertThat(d.getAttempts()).isEqualTo(2);
  }

  @Test
  void settledOrClosedDepositsCannotBeCheckedOut() {
    PurchaseDeposit d = issued();
    d.setStatus(DepositStatus.PAID);
    assertThatThrownBy(() -> service.startCheckout(buyer, order.getId()))
        .hasMessageContaining("already settled");
    d.setStatus(DepositStatus.CANCELLED);
    assertThatThrownBy(() -> service.startCheckout(buyer, order.getId()))
        .hasMessageContaining("closed");
    verify(chapa, never()).initialize(any());
  }

  @Test
  void strangersCannotSeeOrPayTheDeposit() {
    issued();
    PurchaseOrderActor stranger = PurchaseOrderActor.buyer(UUID.randomUUID());
    assertThatThrownBy(() -> service.startCheckout(stranger, order.getId()))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.get(stranger, order.getId()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  // ------------------------------------------------------------------ confirmation

  private PurchaseDeposit pending() {
    PurchaseDeposit d = issued();
    d.setStatus(DepositStatus.PENDING);
    d.setTxRef("PPO-2026-TEST0001-DEP1-AAAAAA");
    d.setAttempts(1);
    when(depositRepository.findByTxRef(d.getTxRef())).thenReturn(Optional.of(d));
    return d;
  }

  @Test
  void confirmMarksPaidFromTheVerifyApiAndPublishesAnEvent() {
    PurchaseDeposit d = pending();
    when(chapa.verify(d.getTxRef()))
        .thenReturn(
            new VerifyResult(
                "success",
                new BigDecimal("85000"),
                "ETB",
                "CHAPA-REF-1",
                d.getTxRef(),
                "card",
                "Visa",
                new BigDecimal("2975")));

    service.confirm(buyer, order.getId());

    assertThat(d.getStatus()).isEqualTo(DepositStatus.PAID);
    assertThat(d.getProviderReference()).isEqualTo("CHAPA-REF-1");
    assertThat(d.getPaymentMethod()).isEqualTo("card");
    assertThat(d.getPaidAt()).isNotNull();
    assertThat(service.blocksCompletion(order)).isFalse();
    verify(eventPublisher).publishEvent(any(PurchaseDepositPaidEvent.class));
  }

  @Test
  void anUnderpaymentIsRecordedAsFailure() {
    PurchaseDeposit d = pending();
    when(chapa.verify(d.getTxRef()))
        .thenReturn(
            new VerifyResult(
                "success", new BigDecimal("100"), "ETB", "R", d.getTxRef(), "card", null, null));
    service.confirm(buyer, order.getId());
    assertThat(d.getStatus()).isEqualTo(DepositStatus.FAILED);
    assertThat(d.getFailureReason()).contains("100");
    verify(eventPublisher, never()).publishEvent(any(PurchaseDepositPaidEvent.class));
  }

  @Test
  void pendingStaysPendingAndFailedIsRecorded() {
    PurchaseDeposit d = pending();
    when(chapa.verify(d.getTxRef()))
        .thenReturn(new VerifyResult("pending", null, null, null, d.getTxRef(), null, null, null));
    service.confirm(buyer, order.getId());
    assertThat(d.getStatus()).isEqualTo(DepositStatus.PENDING);

    when(chapa.verify(d.getTxRef()))
        .thenReturn(new VerifyResult("failed", null, null, null, d.getTxRef(), null, null, null));
    service.confirm(buyer, order.getId());
    assertThat(d.getStatus()).isEqualTo(DepositStatus.FAILED);
  }

  @Test
  void webhookVerifiesThroughTheApiAndIgnoresRepeats() {
    PurchaseDeposit d = pending();
    when(chapa.verify(d.getTxRef()))
        .thenReturn(
            new VerifyResult(
                "success",
                new BigDecimal("85000"),
                "ETB",
                "REF",
                d.getTxRef(),
                "card",
                null,
                null));

    service.handleProviderNotification(d.getTxRef());
    assertThat(d.getStatus()).isEqualTo(DepositStatus.PAID);

    service.handleProviderNotification(d.getTxRef()); // repeat
    verify(chapa).verify(d.getTxRef()); // exactly once
    assertThatThrownBy(() -> service.handleProviderNotification("unknown"))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  // ------------------------------------------------------------------ closing / admin

  @Test
  void closingAnOrderCancelsAnUnpaidDepositAndFlagsAPaidOneForRefund() {
    PurchaseDeposit d = issued();
    service.onOrderClosed(order, "Order cancelled");
    assertThat(d.getStatus()).isEqualTo(DepositStatus.CANCELLED);

    d.setStatus(DepositStatus.PAID);
    service.onOrderClosed(order, "Order rejected");
    assertThat(d.getStatus()).isEqualTo(DepositStatus.REFUND_PENDING);
  }

  @Test
  void adminCanWaiveAnUnpaidDepositAndRecordARefund() {
    PurchaseDeposit d = issued();
    UUID admin = UUID.randomUUID();
    service.waive(admin, order.getId(), "VIP buyer");
    assertThat(d.getStatus()).isEqualTo(DepositStatus.WAIVED);
    assertThat(d.getWaivedByUserId()).isEqualTo(admin);
    assertThat(service.blocksCompletion(order)).isFalse();

    d.setStatus(DepositStatus.PAID);
    assertThatThrownBy(() -> service.waive(admin, order.getId(), "x"))
        .hasMessageContaining("refund it instead");
    d.setStatus(DepositStatus.REFUND_PENDING);
    service.markRefunded(admin, order.getId(), "CHAPA-RFD-1");
    assertThat(d.getStatus()).isEqualTo(DepositStatus.REFUNDED);
    assertThat(d.getRefundReference()).isEqualTo("CHAPA-RFD-1");
  }

  @Test
  void responseExposesCheckoutUrlOnlyWhilePending() {
    PurchaseDeposit d = pending();
    d.setCheckoutUrl("https://checkout.chapa.co/x");
    assertThat(service.toResponse(order).getCheckoutUrl()).isEqualTo("https://checkout.chapa.co/x");
    d.setStatus(DepositStatus.PAID);
    assertThat(service.toResponse(order).getCheckoutUrl()).isNull();
    assertThat(service.toResponse(order).getCheckoutAvailable()).isTrue();
  }
}
