package com.housingplatform.purchase.service.impl;

import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.payment.chapa.ChapaClient;
import com.housingplatform.payment.chapa.ChapaClient.InitializeRequest;
import com.housingplatform.payment.chapa.ChapaClient.InitializeResult;
import com.housingplatform.payment.chapa.ChapaClient.VerifyResult;
import com.housingplatform.payment.chapa.ChapaProperties;
import com.housingplatform.purchase.config.PurchaseProviderProperties;
import com.housingplatform.purchase.domain.AgreementTemplate.AgreementType;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder;
import com.housingplatform.purchase.domain.PurchaseAgreement;
import com.housingplatform.purchase.domain.PurchaseDeposit;
import com.housingplatform.purchase.domain.PurchaseDeposit.DepositStatus;
import com.housingplatform.purchase.dto.DepositCheckoutResponse;
import com.housingplatform.purchase.dto.PurchaseDepositResponse;
import com.housingplatform.purchase.repository.PropertyPurchaseOrderRepository;
import com.housingplatform.purchase.repository.PurchaseDepositRepository;
import com.housingplatform.purchase.service.DepositPolicy;
import com.housingplatform.purchase.service.PurchaseDepositService;
import com.housingplatform.purchase.service.PurchaseOrderAccess;
import com.housingplatform.purchase.service.PurchaseOrderActor;
import com.housingplatform.purchase.service.PurchaseOrderEvents.PurchaseDepositPaidEvent;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.time.LocalDateTime;
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
  public void issueForOrder(PropertyPurchaseOrder order) {
    if (!policy.isEnabled() || order.getDeposit() != null) {
      return;
    }
    PurchaseDeposit deposit =
        PurchaseDeposit.builder()
            .purchaseOrder(order)
            .amount(policy.amountFor(order.getListedPrice(), order.getCurrency()))
            .currency(order.getCurrency())
            .status(DepositStatus.DUE)
            .dueAt(LocalDateTime.now().plusDays(policy.dueDays()))
            .build();
    order.setDeposit(deposit);
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
  public DepositCheckoutResponse startCheckout(PurchaseOrderActor buyer, UUID orderId) {
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
                order.getContactPhone(),
                callbackUrl(),
                returnUrl(order),
                "Deposit " + order.getOrderNumber(),
                "Reservation deposit to " + provider.getName() + " for " + order.getOrderNumber()));
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

  static String newTxRef(String orderNumber, int attempt) {
    String random =
        UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
    return orderNumber + "-DEP" + attempt + "-" + random;
  }

  private String callbackUrl() {
    String base = chapaProperties.getPublicApiBaseUrl();
    if (base == null || base.isBlank()) {
      return null; // Chapa then relies on the dashboard-configured webhook URL
    }
    return base.replaceAll("/$", "") + "/api/v1/payments/chapa/webhook";
  }

  private String returnUrl(PropertyPurchaseOrder order) {
    return frontendBaseUrl.replaceAll("/$", "")
        + "/purchase-orders/"
        + order.getId()
        + "?deposit=return";
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
