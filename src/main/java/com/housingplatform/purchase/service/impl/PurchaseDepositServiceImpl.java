package com.housingplatform.purchase.service.impl;

import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.payment.chapa.ChapaClient;
import com.housingplatform.payment.chapa.ChapaClient.InitializeRequest;
import com.housingplatform.payment.chapa.ChapaClient.InitializeResult;
import com.housingplatform.payment.chapa.ChapaClient.VerifyResult;
import com.housingplatform.payment.chapa.ChapaPaymentMethod;
import com.housingplatform.payment.chapa.ChapaProperties;
import com.housingplatform.purchase.config.PurchaseProviderProperties;
import com.housingplatform.purchase.domain.AgreementTemplate.AgreementType;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder;
import com.housingplatform.purchase.domain.PurchaseAgreement;
import com.housingplatform.purchase.domain.PurchaseDeposit;
import com.housingplatform.purchase.domain.PurchaseDeposit.DepositStatus;
import com.housingplatform.purchase.dto.DepositCheckoutResponse;
import com.housingplatform.purchase.dto.PurchaseDepositResponse;
import com.housingplatform.purchase.dto.PurchasePreviewResponse;
import com.housingplatform.purchase.repository.PropertyPurchaseOrderRepository;
import com.housingplatform.purchase.repository.PurchaseDepositRepository;
import com.housingplatform.purchase.service.DepositPolicy;
import com.housingplatform.purchase.service.PurchaseDepositService;
import com.housingplatform.purchase.service.PurchaseOrderAccess;
import com.housingplatform.purchase.service.PurchaseOrderActor;
import com.housingplatform.purchase.service.PurchaseOrderEvents.PurchaseDepositPaidEvent;
import com.housingplatform.shared.domain.Currency;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PurchaseDepositServiceImpl implements PurchaseDepositService {

  private final PurchaseDepositRepository depositRepository;
  private final PropertyPurchaseOrderRepository orderRepository;
  private final UserRepository userRepository;
  private final DepositPolicy policy;
  private final ChapaClient chapa;
  private final ChapaProperties chapaProperties;
  private final PurchaseProviderProperties provider;
  private final ApplicationEventPublisher eventPublisher;

  @Value("${app.frontend-base-url:http://localhost:5173}")
  private String frontendBaseUrl = "http://localhost:5173";

  // ------------------------------------------------------------------ workflow hooks

  @Override
  public void issueForOrder(
      PropertyPurchaseOrder order, ChapaPaymentMethod preferredMethod, Currency payCurrency) {
    if (!policy.isEnabled() || order.getDeposit() != null) {
      return;
    }
    BigDecimal amount = policy.amountFor(order.getListedPrice(), order.getCurrency());
    PurchaseDeposit deposit =
        PurchaseDeposit.builder()
            .purchaseOrder(order)
            .amount(amount)
            .currency(order.getCurrency())
            .status(DepositStatus.DUE)
            .dueAt(LocalDateTime.now().plusDays(policy.dueDays()))
            .preferredMethod(preferredMethod)
            .build();
    if (convertsToUsd(order.getCurrency(), payCurrency)) {
      deposit.setBaseAmount(amount);
      deposit.setBaseCurrency(order.getCurrency());
      deposit.setExchangeRate(policy.etbPerUsd());
      deposit.setAmount(policy.toUsd(amount));
      deposit.setCurrency(Currency.USD);
    }
    deposit.setPreferredMethod(methodFor(deposit.getCurrency(), preferredMethod));
    order.setDeposit(deposit);
  }

  /** True when an ETB deposit is to be paid in USD; fails if that option is off. */
  private boolean convertsToUsd(Currency orderCurrency, Currency payCurrency) {
    if (payCurrency != Currency.USD || orderCurrency == Currency.USD) {
      return false;
    }
    if (policy.etbPerUsd() == null) {
      throw new BusinessException("Paying the deposit in USD is not available at the moment");
    }
    return true;
  }

  /** Mobile wallets only take birr: a USD deposit is always paid by card. */
  private static ChapaPaymentMethod methodFor(Currency currency, ChapaPaymentMethod method) {
    return currency == Currency.USD ? ChapaPaymentMethod.CARD : method;
  }

  private static List<ChapaPaymentMethod> methodsFor(Currency currency) {
    return currency == Currency.USD
        ? List.of(ChapaPaymentMethod.CARD)
        : List.of(ChapaPaymentMethod.values());
  }

  @Override
  public PurchasePreviewResponse.DepositQuote quote(
      BigDecimal price, Currency currency, Currency payCurrency) {
    if (!policy.isEnabled()) {
      return null;
    }
    BigDecimal amount = policy.amountFor(price, currency);
    boolean usdOption = currency != Currency.USD && policy.etbPerUsd() != null;
    PurchasePreviewResponse.DepositQuote.DepositQuoteBuilder q =
        PurchasePreviewResponse.DepositQuote.builder()
            .amount(amount)
            .currency(currency)
            .checkoutAvailable(chapa.isConfigured())
            .paymentMethods(methodsFor(currency))
            .usdAmount(usdOption ? policy.toUsd(amount) : null);
    if (convertsToUsd(currency, payCurrency)) {
      q.amount(policy.toUsd(amount))
          .currency(Currency.USD)
          .paymentMethods(methodsFor(Currency.USD))
          .baseAmount(amount)
          .baseCurrency(currency)
          .exchangeRate(policy.etbPerUsd());
    }
    return q.build();
  }

  @Override
  public void onOrderClosed(PropertyPurchaseOrder order, String reason) {
    PurchaseDeposit d = order.getDeposit();
    if (d == null) {
      return;
    }
    switch (d.getStatus()) {
      case DUE, PENDING, FAILED -> {
        d.setStatus(DepositStatus.CANCELLED);
        d.setFailureReason(reason);
      }
      case PAID -> d.setStatus(DepositStatus.REFUND_PENDING);
      default -> {}
    }
  }

  @Override
  @Transactional(readOnly = true)
  public boolean blocksCompletion(PropertyPurchaseOrder order) {
    PurchaseDeposit d = order.getDeposit();
    return d != null && !d.isSettled();
  }

  // ------------------------------------------------------------------ buyer

  @Override
  @Transactional(readOnly = true)
  public PurchaseDepositResponse get(PurchaseOrderActor actor, UUID orderId) {
    PropertyPurchaseOrder order = loadViewable(actor, orderId);
    if (order.getDeposit() == null) {
      throw new ResourceNotFoundException("PurchaseDeposit", orderId);
    }
    return toResponse(order);
  }

  @Override
  public DepositCheckoutResponse startCheckout(
      PurchaseOrderActor buyer, UUID orderId, ChapaPaymentMethod method) {
    PropertyPurchaseOrder order = loadOwn(buyer, orderId);
    PurchaseDeposit d = requireDeposit(order);
    if (!chapa.isConfigured()) {
      throw new BusinessException("Card payments are not configured on this server yet");
    }
    if (d.getStatus() == DepositStatus.PAID || d.getStatus() == DepositStatus.WAIVED) {
      throw new BusinessException("The reservation deposit is already settled");
    }
    if (d.getStatus() == DepositStatus.CANCELLED
        || d.getStatus() == DepositStatus.REFUND_PENDING
        || d.getStatus() == DepositStatus.REFUNDED) {
      throw new BusinessException("This order is closed; no deposit is due");
    }
    if (termsPending(order)) {
      throw new BusinessException(
          "Please read and sign the reservation deposit terms before paying the deposit");
    }
    // A pending checkout can be resumed until the provider reports on it.
    if (d.getStatus() == DepositStatus.PENDING
        && d.getCheckoutUrl() != null
        && d.getTxRef() != null) {
      VerifyResult existing = safeVerify(d.getTxRef());
      if (existing != null && existing.isSuccess()) {
        markPaid(order, d, existing);
        throw new BusinessException("The reservation deposit has already been paid");
      }
      if (existing == null || !existing.isFailed()) {
        return checkoutResponse(d);
      }
      markFailed(d, "Previous attempt failed at the provider");
    }

    User buyerUser = userRepository.findById(order.getBuyerId()).orElse(null);
    // Only a fresh checkout takes a new method; a pending one above is resumed as it was started.
    // Without an explicit choice, the buyer's saved preference applies.
    if (method == null && d.getPreferredMethod() == null && buyerUser != null) {
      method = buyerUser.getPreferredPaymentMethod();
    }
    if (method != null) {
      d.setPreferredMethod(methodFor(d.getCurrency(), method));
    }
    String txRef = newTxRef(order.getOrderNumber(), d.getAttempts() + 1);
    InitializeResult result =
        chapa.initialize(
            new InitializeRequest(
                d.getAmount(),
                d.getCurrency().getCode(),
                txRef,
                order.getContactEmail() != null
                    ? order.getContactEmail()
                    : (buyerUser != null ? buyerUser.getEmail() : null),
                buyerUser != null ? buyerUser.getFirstName() : null,
                buyerUser != null ? buyerUser.getLastName() : null,
                PaymentPhones.forCheckout(
                    buyerUser, d.getPreferredMethod(), order.getContactPhone()),
                callbackUrl(),
                returnUrl(order),
                "Deposit " + order.getOrderNumber(),
                "Reservation deposit to " + provider.getName() + " for " + order.getOrderNumber(),
                d.getPreferredMethod() != null
                    ? d.getPreferredMethod().preferredPaymentMethods()
                    : List.of()));
    d.setTxRef(txRef);
    d.setCheckoutUrl(result.checkoutUrl());
    d.setStatus(DepositStatus.PENDING);
    d.setFailureReason(null);
    d.setAttempts(d.getAttempts() + 1);
    depositRepository.save(d);
    return checkoutResponse(d);
  }

  @Override
  public PurchaseDepositResponse confirm(PurchaseOrderActor buyer, UUID orderId) {
    PropertyPurchaseOrder order = loadOwn(buyer, orderId);
    PurchaseDeposit d = requireDeposit(order);
    if (d.getStatus() == DepositStatus.PENDING && d.getTxRef() != null) {
      VerifyResult result = chapa.verify(d.getTxRef());
      apply(order, d, result);
    }
    return toResponse(order);
  }

  // ------------------------------------------------------------------ provider

  @Override
  public void handleProviderNotification(String txRef) {
    PurchaseDeposit d =
        depositRepository
            .findByTxRef(txRef)
            .orElseThrow(() -> new ResourceNotFoundException("PurchaseDeposit", txRef));
    if (d.getStatus() != DepositStatus.PENDING) {
      return; // already settled; webhooks may repeat
    }
    // Never trust the webhook body for the outcome: ask the provider.
    apply(d.getPurchaseOrder(), d, chapa.verify(txRef));
  }

  // ------------------------------------------------------------------ admin

  @Override
  public PurchaseDepositResponse waive(UUID adminUserId, UUID orderId, String reason) {
    PropertyPurchaseOrder order = loadOrder(orderId);
    PurchaseDeposit d = requireDeposit(order);
    if (d.getStatus() == DepositStatus.PAID) {
      throw new BusinessException("The deposit is already paid; refund it instead of waiving it");
    }
    d.setStatus(DepositStatus.WAIVED);
    d.setWaivedByUserId(adminUserId);
    d.setWaiveReason(reason);
    depositRepository.save(d);
    return toResponse(order);
  }

  @Override
  public PurchaseDepositResponse markRefunded(
      UUID adminUserId, UUID orderId, String refundReference) {
    PropertyPurchaseOrder order = loadOrder(orderId);
    PurchaseDeposit d = requireDeposit(order);
    if (d.getStatus() != DepositStatus.REFUND_PENDING && d.getStatus() != DepositStatus.PAID) {
      throw new BusinessException("Only a paid deposit can be marked as refunded");
    }
    d.setStatus(DepositStatus.REFUNDED);
    d.setRefundReference(refundReference);
    d.setRefundedAt(LocalDateTime.now());
    d.setWaivedByUserId(adminUserId);
    depositRepository.save(d);
    return toResponse(order);
  }

  // ------------------------------------------------------------------ mapping

  @Override
  public PurchaseDepositResponse toResponse(PropertyPurchaseOrder order) {
    PurchaseDeposit d = order.getDeposit();
    if (d == null) {
      return null;
    }
    return PurchaseDepositResponse.builder()
        .amount(d.getAmount())
        .currency(d.getCurrency())
        .status(d.getStatus())
        .dueAt(d.getDueAt())
        .provider(d.getProvider())
        .txRef(d.getTxRef())
        .checkoutUrl(d.getStatus() == DepositStatus.PENDING ? d.getCheckoutUrl() : null)
        .providerReference(d.getProviderReference())
        .paymentMethod(d.getPaymentMethod())
        .preferredMethod(d.getPreferredMethod())
        .baseAmount(d.getBaseAmount())
        .baseCurrency(d.getBaseCurrency())
        .exchangeRate(d.getExchangeRate())
        .paidAt(d.getPaidAt())
        .failureReason(d.getFailureReason())
        .attempts(d.getAttempts())
        .termsPending(termsPending(order))
        .checkoutAvailable(chapa.isConfigured())
        .refundReference(d.getRefundReference())
        .refundedAt(d.getRefundedAt())
        .waiveReason(d.getWaiveReason())
        .build();
  }

  // ------------------------------------------------------------------ helpers

  private void apply(PropertyPurchaseOrder order, PurchaseDeposit d, VerifyResult result) {
    if (result.isSuccess()) {
      if (result.amount() != null && result.amount().compareTo(d.getAmount()) < 0) {
        markFailed(
            d, "Provider reported " + result.amount() + " but " + d.getAmount() + " was due");
        return;
      }
      markPaid(order, d, result);
    } else if (result.isFailed()) {
      markFailed(d, "Payment failed at the provider");
    }
    // "pending": leave as is; the webhook or the next confirm will settle it.
  }

  private void markPaid(PropertyPurchaseOrder order, PurchaseDeposit d, VerifyResult result) {
    d.setStatus(DepositStatus.PAID);
    d.setPaidAt(LocalDateTime.now());
    d.setProviderReference(result.reference());
    d.setPaymentMethod(result.method() != null ? result.method() : result.type());
    d.setFailureReason(null);
    depositRepository.save(d);
    eventPublisher.publishEvent(new PurchaseDepositPaidEvent(order.getId()));
  }

  private void markFailed(PurchaseDeposit d, String reason) {
    d.setStatus(DepositStatus.FAILED);
    d.setFailureReason(reason);
    depositRepository.save(d);
  }

  private VerifyResult safeVerify(String txRef) {
    try {
      return chapa.verify(txRef);
    } catch (RuntimeException e) {
      log.warn("Could not verify {} before restarting checkout: {}", txRef, e.getMessage());
      return null;
    }
  }

  /** The deposit terms agreement, when one has been issued, must be signed before paying. */
  private static boolean termsPending(PropertyPurchaseOrder order) {
    return order.getAgreements().stream()
        .anyMatch(
            a ->
                a.getType() == AgreementType.RESERVATION_DEPOSIT_TERMS
                    && a.getStatus() == PurchaseAgreement.AgreementStatus.PENDING_BUYER_SIGNATURE);
  }

  private DepositCheckoutResponse checkoutResponse(PurchaseDeposit d) {
    return DepositCheckoutResponse.builder()
        .checkoutUrl(d.getCheckoutUrl())
        .txRef(d.getTxRef())
        .amount(d.getAmount())
        .currency(d.getCurrency())
        .provider(d.getProvider())
        .build();
  }

  /**
   * Chapa Global rejects references over this length ("Reference must be at most 20 characters").
   */
  static final int MAX_TX_REF_LENGTH = 20;

  /**
   * {@code <order code>-D<attempt>-<random>}, e.g. {@code 3F9A1C2B-D1-7K2Q} for order
   * PPO-2026-3F9A1C2B. The "PPO-year-" prefix is dropped to stay within Chapa's 20-character limit.
   */
  static String newTxRef(String orderNumber, int attempt) {
    String code = orderNumber.substring(orderNumber.lastIndexOf('-') + 1);
    String random =
        UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase(Locale.ROOT);
    String suffix = "-D" + attempt + "-" + random;
    int room = MAX_TX_REF_LENGTH - suffix.length();
    return (code.length() > room ? code.substring(0, room) : code) + suffix;
  }

  private String callbackUrl() {
    String base = chapaProperties.getPublicApiBaseUrl();
    if (base == null || base.isBlank()) {
      return null; // Chapa then relies on the dashboard-configured webhook URL
    }
    return base.replaceAll("/$", "") + "/api/v1/payments/chapa/webhook";
  }

  private String returnUrl(PropertyPurchaseOrder order) {
    return returnBase(frontendBaseUrl, chapaProperties.getPublicApiBaseUrl())
        + "/purchase-orders/"
        + order.getId()
        + "?deposit=return";
  }

  /**
   * Chapa rejects non-https return URLs ("Callback URL must start with https://"), so an unset
   * FRONTEND_BASE_URL (default http://localhost:5173) falls back to the public site URL, which
   * serves the web app too.
   */
  static String returnBase(String frontendBase, String publicBase) {
    String base =
        frontendBase != null && frontendBase.startsWith("https://")
                || publicBase == null
                || !publicBase.startsWith("https://")
            ? frontendBase
            : publicBase;
    return base == null ? "" : base.replaceAll("/$", "");
  }

  private PropertyPurchaseOrder loadOrder(UUID orderId) {
    return orderRepository
        .findById(orderId)
        .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", orderId));
  }

  private PropertyPurchaseOrder loadViewable(PurchaseOrderActor actor, UUID orderId) {
    PropertyPurchaseOrder order = loadOrder(orderId);
    if (!PurchaseOrderAccess.canView(actor, order)) {
      throw new ResourceNotFoundException("PurchaseOrder", orderId);
    }
    return order;
  }

  private PropertyPurchaseOrder loadOwn(PurchaseOrderActor buyer, UUID orderId) {
    PropertyPurchaseOrder order = loadOrder(orderId);
    if (!PurchaseOrderAccess.isBuyer(buyer, order)) {
      throw new ResourceNotFoundException("PurchaseOrder", orderId);
    }
    return order;
  }

  private static PurchaseDeposit requireDeposit(PropertyPurchaseOrder order) {
    if (order.getDeposit() == null) {
      throw new BusinessException(
          "No reservation deposit is due on order " + order.getOrderNumber());
    }
    return order.getDeposit();
  }
}
