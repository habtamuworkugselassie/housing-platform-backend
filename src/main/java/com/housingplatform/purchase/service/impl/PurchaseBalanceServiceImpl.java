package com.housingplatform.purchase.service.impl;

import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.media.service.MediaStorageService;
import com.housingplatform.payment.chapa.ChapaClient;
import com.housingplatform.payment.chapa.ChapaClient.InitializeRequest;
import com.housingplatform.payment.chapa.ChapaClient.VerifyResult;
import com.housingplatform.payment.chapa.ChapaPaymentMethod;
import com.housingplatform.payment.chapa.ChapaProperties;
import com.housingplatform.property.service.PropertyDocumentService.DocumentFile;
import com.housingplatform.purchase.config.PurchaseProviderProperties;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder.PurchaseOrderStatus;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder.PurchaseType;
import com.housingplatform.purchase.domain.PurchaseBalanceInstalment;
import com.housingplatform.purchase.domain.PurchaseBalancePayment;
import com.housingplatform.purchase.domain.PurchaseBalancePayment.Channel;
import com.housingplatform.purchase.domain.PurchaseBalancePayment.Purpose;
import com.housingplatform.purchase.domain.PurchaseBalancePayment.Status;
import com.housingplatform.purchase.domain.PurchaseDeposit;
import com.housingplatform.purchase.domain.PurchaseDeposit.DepositStatus;
import com.housingplatform.purchase.domain.PurchaseOrderFinancing;
import com.housingplatform.purchase.dto.DepositCheckoutResponse;
import com.housingplatform.purchase.dto.PurchaseBalanceResponse;
import com.housingplatform.purchase.repository.PropertyPurchaseOrderRepository;
import com.housingplatform.purchase.repository.PurchaseBalanceInstalmentRepository;
import com.housingplatform.purchase.repository.PurchaseBalancePaymentRepository;
import com.housingplatform.purchase.service.ProviderBankAccountSettings;
import com.housingplatform.purchase.service.PurchaseBalanceService;
import com.housingplatform.purchase.service.PurchaseOrderAccess;
import com.housingplatform.purchase.service.PurchaseOrderActor;
import com.housingplatform.shared.domain.Currency;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ForbiddenOperationException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PurchaseBalanceServiceImpl implements PurchaseBalanceService {

  /** After the seller accepts and while the order is open, the balance can be paid. */
  static final Set<PurchaseOrderStatus> PAYABLE =
      EnumSet.of(
          PurchaseOrderStatus.AWAITING_PAYMENT,
          PurchaseOrderStatus.AWAITING_FINANCING,
          PurchaseOrderStatus.FINANCING_APPROVED,
          PurchaseOrderStatus.FINANCING_PARTIALLY_APPROVED);

  static final long MAX_SLIP_BYTES = 10L * 1024 * 1024;
  static final Set<String> SLIP_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");
  static final int MAX_INSTALMENTS = 24;

  private final PropertyPurchaseOrderRepository orderRepository;
  private final PurchaseBalanceInstalmentRepository instalmentRepository;
  private final PurchaseBalancePaymentRepository paymentRepository;
  private final UserRepository userRepository;
  private final ChapaClient chapa;
  private final ChapaProperties chapaProperties;
  private final PurchaseProviderProperties provider;
  private final ProviderBankAccountSettings bankAccount;
  private final MediaStorageService storage;
  private final com.housingplatform.purchase.service.OnlinePaymentLimits onlineLimits;

  @Value("${app.frontend-base-url:http://localhost:5173}")
  private String frontendBaseUrl = "http://localhost:5173";

  // ------------------------------------------------------------------ reads

  @Override
  @Transactional(readOnly = true)
  public PurchaseBalanceResponse get(PurchaseOrderActor actor, UUID orderId) {
    PropertyPurchaseOrder order = loadOrder(orderId);
    if (!PurchaseOrderAccess.canView(actor, order)) {
      throw new ResourceNotFoundException("PurchaseOrder", orderId);
    }
    return toResponse(order);
  }

  // ------------------------------------------------------------------ seller

  @Override
  public PurchaseBalanceResponse setSchedule(
      PurchaseOrderActor seller, UUID orderId, List<InstalmentLine> lines) {
    PropertyPurchaseOrder order = loadManaged(seller, orderId);
    requireOpen(order);
    List<PurchaseBalancePayment> payments = payments(order);
    if (!seller.admin() && sum(payments, Status.PAID, Purpose.BALANCE).signum() > 0) {
      throw new BusinessException(
          "Payments have already been made; ask the platform to change the schedule");
    }
    if (lines == null || lines.isEmpty() || lines.size() > MAX_INSTALMENTS) {
      throw new BusinessException("Give between 1 and " + MAX_INSTALMENTS + " instalments");
    }
    BigDecimal total = BigDecimal.ZERO;
    for (InstalmentLine l : lines) {
      if (l.amount() == null || l.amount().signum() <= 0) {
        throw new BusinessException("Every instalment needs an amount above zero");
      }
      total = total.add(l.amount().setScale(2, RoundingMode.HALF_UP));
    }
    BigDecimal due = balanceDue(order);
    if (total.compareTo(due) != 0) {
      throw new BusinessException(
          "The instalments add up to "
              + total.toPlainString()
              + " but the balance due is "
              + due.toPlainString()
              + " "
              + order.getCurrency().getCode());
    }
    instalmentRepository.deleteByPurchaseOrderId(order.getId());
    instalmentRepository.flush();
    int seq = 1;
    for (InstalmentLine l : lines) {
      instalmentRepository.save(
          PurchaseBalanceInstalment.builder()
              .purchaseOrder(order)
              .sequence(seq)
              .label(
                  l.label() != null && !l.label().isBlank()
                      ? trim(l.label(), 100)
                      : "Instalment " + seq)
              .amount(l.amount().setScale(2, RoundingMode.HALF_UP))
              .dueDate(l.dueDate())
              .build());
      seq++;
    }
    return toResponse(order);
  }

  @Override
  public PurchaseBalanceResponse reviewTransfer(
      PurchaseOrderActor seller, UUID orderId, UUID paymentId, boolean approve, String note) {
    PropertyPurchaseOrder order = loadManaged(seller, orderId);
    PurchaseBalancePayment p = requirePayment(order, paymentId);
    if (p.getStatus() != Status.SUBMITTED) {
      throw new BusinessException("This transfer has already been reviewed");
    }
    if (!approve && (note == null || note.isBlank())) {
      throw new BusinessException("Say why the transfer is rejected so the buyer can fix it");
    }
    if (approve) {
      requireOpen(order);
      requireWithinRemaining(order, p.getAmount(), p.getId(), p.getPurpose());
      p.setStatus(Status.PAID);
      p.setPaidAt(LocalDateTime.now());
    } else {
      p.setStatus(Status.REJECTED);
    }
    p.setNote(trim(note, 2000));
    p.setReviewedByUserId(seller.userId());
    p.setReviewedAt(LocalDateTime.now());
    paymentRepository.save(p);
    return toResponse(order);
  }

  @Override
  public PurchaseBalanceResponse record(
      PurchaseOrderActor seller,
      UUID orderId,
      BigDecimal amount,
      String reference,
      LocalDate paidOn,
      String note,
      Purpose purpose) {
    PropertyPurchaseOrder order = loadManaged(seller, orderId);
    requireOpen(order);
    Purpose what = purpose != null ? purpose : Purpose.BALANCE;
    BigDecimal value = positive(amount);
    requireWithinRemaining(order, value, null, what);
    if (reference == null || reference.isBlank()) {
      throw new BusinessException("Give the bank or receipt reference of the payment");
    }
    paymentRepository.save(
        PurchaseBalancePayment.builder()
            .purchaseOrder(order)
            .channel(Channel.RECORDED)
            .purpose(what)
            .status(Status.PAID)
            .amount(value)
            .currency(order.getCurrency())
            .reference(trim(reference, 255))
            .paidOn(paidOn)
            .note(trim(note, 2000))
            .submittedByUserId(seller.userId())
            .reviewedByUserId(seller.userId())
            .reviewedAt(LocalDateTime.now())
            .paidAt(LocalDateTime.now())
            .build());
    return toResponse(order);
  }

  // ------------------------------------------------------------------ buyer: online

  @Override
  public DepositCheckoutResponse startOnline(
      PurchaseOrderActor buyer,
      UUID orderId,
      BigDecimal amount,
      ChapaPaymentMethod method,
      Purpose purpose) {
    Purpose what = purpose != null ? purpose : Purpose.BALANCE;
    PropertyPurchaseOrder order = loadOwn(buyer, orderId);
    requirePayable(order);
    if (!chapa.isConfigured()) {
      throw new BusinessException("Online payments are not configured on this server yet");
    }
    settlePending(order);
    BigDecimal value = positive(amount);
    requireWithinRemaining(order, value, null, what);
    BigDecimal max = onlineLimits.maxFor(order.getCurrency());
    if (max != null && value.compareTo(max) > 0) {
      throw new BusinessException(
          "Online payments are limited to "
              + max.stripTrailingZeros().toPlainString()
              + " "
              + order.getCurrency().getCode()
              + " each. Pay in several parts or by bank transfer");
    }

    ChapaPaymentMethod chosen =
        order.getCurrency() == Currency.USD ? ChapaPaymentMethod.CARD : method;
    long attempt =
        payments(order).stream().filter(x -> x.getChannel() == Channel.ONLINE).count() + 1;
    String txRef = newTxRef(order.getOrderNumber(), what == Purpose.FEES ? "F" : "B", attempt);
    User user = userRepository.findById(order.getBuyerId()).orElse(null);
    var result =
        chapa.initialize(
            new InitializeRequest(
                value,
                order.getCurrency().getCode(),
                txRef,
                order.getContactEmail() != null
                    ? order.getContactEmail()
                    : (user != null ? user.getEmail() : null),
                user != null ? user.getFirstName() : null,
                user != null ? user.getLastName() : null,
                order.getContactPhone(),
                callbackUrl(),
                returnUrl(order),
                (what == Purpose.FEES ? "Fee " : "Balance ") + order.getOrderNumber(),
                (what == Purpose.FEES ? "Service fee and VAT to " : "Balance payment to ")
                    + provider.getName()
                    + " for "
                    + order.getOrderNumber(),
                chosen != null ? chosen.preferredPaymentMethods() : List.of()));
    PurchaseBalancePayment p =
        paymentRepository.save(
            PurchaseBalancePayment.builder()
                .purchaseOrder(order)
                .channel(Channel.ONLINE)
                .purpose(what)
                .status(Status.PENDING)
                .amount(value)
                .currency(order.getCurrency())
                .txRef(txRef)
                .checkoutUrl(result.checkoutUrl())
                .preferredMethod(chosen)
                .submittedByUserId(buyer.userId())
                .build());
    return DepositCheckoutResponse.builder()
        .checkoutUrl(p.getCheckoutUrl())
        .txRef(p.getTxRef())
        .amount(p.getAmount())
        .currency(p.getCurrency())
        .provider("CHAPA")
        .build();
  }

  @Override
  public PurchaseBalanceResponse confirmOnline(PurchaseOrderActor buyer, UUID orderId) {
    PropertyPurchaseOrder order = loadOwn(buyer, orderId);
    settlePending(order);
    return toResponse(order);
  }

  // ------------------------------------------------------------------ buyer: bank transfer

  @Override
  public PurchaseBalanceResponse reportTransfer(
      PurchaseOrderActor buyer,
      UUID orderId,
      BigDecimal amount,
      String reference,
      LocalDate paidOn,
      MultipartFile slip,
      Purpose purpose) {
    PropertyPurchaseOrder order = loadOwn(buyer, orderId);
    requirePayable(order);
    Purpose what = purpose != null ? purpose : Purpose.BALANCE;
    BigDecimal value = positive(amount);
    requireWithinRemaining(order, value, null, what);
    if (reference == null || reference.isBlank()) {
      throw new BusinessException("Give the bank's transaction reference from your receipt");
    }
    if (slip == null || slip.isEmpty()) {
      throw new BusinessException("Attach the bank receipt (PDF, JPG or PNG)");
    }
    if (slip.getSize() > MAX_SLIP_BYTES) {
      throw new BusinessException("The receipt is larger than 10 MB");
    }
    String type =
        PropertyDocumentServiceTypes.normalise(slip.getContentType(), slip.getOriginalFilename());
    if (!SLIP_TYPES.contains(type)) {
      throw new BusinessException("Attach the receipt as a PDF, JPG or PNG file");
    }
    String url = storage.save(slip, "private/balance-slips/" + order.getId());
    paymentRepository.save(
        PurchaseBalancePayment.builder()
            .purchaseOrder(order)
            .channel(Channel.BANK_TRANSFER)
            .purpose(what)
            .status(Status.SUBMITTED)
            .amount(value)
            .currency(order.getCurrency())
            .reference(trim(reference, 255))
            .paidOn(paidOn)
            .slipUrl(url)
            .slipFileName(safeName(slip.getOriginalFilename()))
            .slipContentType(type)
            .submittedByUserId(buyer.userId())
            .build());
    return toResponse(order);
  }

  @Override
  @Transactional(readOnly = true)
  public DocumentFile slip(PurchaseOrderActor actor, UUID orderId, UUID paymentId)
      throws IOException {
    PropertyPurchaseOrder order = loadOrder(orderId);
    if (!PurchaseOrderAccess.isBuyer(actor, order) && !PurchaseOrderAccess.canSell(actor, order)) {
      throw new ResourceNotFoundException("PurchaseOrder", orderId);
    }
    PurchaseBalancePayment p = requirePayment(order, paymentId);
    if (p.getSlipUrl() == null) {
      throw new ResourceNotFoundException("BalancePaymentSlip", paymentId);
    }
    Path path = storage.resolveUploadPath(p.getSlipUrl());
    return new DocumentFile(
        path,
        p.getSlipFileName() != null ? p.getSlipFileName() : "receipt",
        p.getSlipContentType() != null ? p.getSlipContentType() : "application/octet-stream",
        Files.size(path));
  }

  // ------------------------------------------------------------------ provider

  @Override
  @Transactional(readOnly = true)
  public boolean ownsTxRef(String txRef) {
    return txRef != null && paymentRepository.findByTxRef(txRef).isPresent();
  }

  @Override
  public void handleProviderNotification(String txRef) {
    PurchaseBalancePayment p =
        paymentRepository
            .findByTxRef(txRef)
            .orElseThrow(() -> new ResourceNotFoundException("BalancePayment", txRef));
    if (p.getStatus() != Status.PENDING) {
      return; // settled already; webhooks repeat
    }
    apply(p, chapa.verify(txRef));
  }

  // ------------------------------------------------------------------ workflow

  @Override
  @Transactional(readOnly = true)
  public boolean blocksCompletion(PropertyPurchaseOrder order) {
    List<PurchaseBalancePayment> payments = payments(order);
    return remaining(order, payments, Purpose.BALANCE).signum() > 0
        || remaining(order, payments, Purpose.FEES).signum() > 0;
  }

  @Override
  public void onOrderClosed(PropertyPurchaseOrder order) {
    for (PurchaseBalancePayment p : payments(order)) {
      switch (p.getStatus()) {
        case PENDING, SUBMITTED -> {
          p.setStatus(Status.CANCELLED);
          p.setNote("Order closed");
        }
        case PAID -> p.setStatus(Status.REFUND_PENDING);
        default -> {}
      }
    }
  }

  // ------------------------------------------------------------------ amounts

  /** Price minus the reservation deposit (unless waived or cancelled) and any bank loan. */
  static BigDecimal balanceDue(PropertyPurchaseOrder order) {
    BigDecimal due = order.getListedPrice().subtract(depositCredit(order)).subtract(loan(order));
    return due.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
  }

  static BigDecimal depositCredit(PropertyPurchaseOrder order) {
    PurchaseDeposit d = order.getDeposit();
    if (d == null
        || d.getStatus() == DepositStatus.WAIVED
        || d.getStatus() == DepositStatus.CANCELLED
        || d.getStatus() == DepositStatus.REFUND_PENDING
        || d.getStatus() == DepositStatus.REFUNDED) {
      return BigDecimal.ZERO;
    }
    // A deposit paid in another currency counts at the order-currency amount it stood for.
    if (d.getBaseAmount() != null && d.getBaseCurrency() == order.getCurrency()) {
      return d.getBaseAmount();
    }
    return d.getCurrency() == order.getCurrency() ? d.getAmount() : BigDecimal.ZERO;
  }

  static BigDecimal loan(PropertyPurchaseOrder order) {
    PurchaseOrderFinancing f = order.getFinancing();
    if (order.getPurchaseType() != PurchaseType.BANK_FINANCED || f == null) {
      return BigDecimal.ZERO;
    }
    BigDecimal amount =
        f.getApprovedAmount() != null ? f.getApprovedAmount() : f.getFinancedAmount();
    return amount != null ? amount : BigDecimal.ZERO;
  }

  static BigDecimal due(PropertyPurchaseOrder order, Purpose purpose) {
    return purpose == Purpose.FEES
        ? order.feesDue().setScale(2, RoundingMode.HALF_UP)
        : balanceDue(order);
  }

  private static BigDecimal remaining(
      PropertyPurchaseOrder order, List<PurchaseBalancePayment> payments, Purpose purpose) {
    return due(order, purpose).subtract(sum(payments, Status.PAID, purpose)).max(BigDecimal.ZERO);
  }

  /** New money must fit in what is left after paid and in-flight payments. */
  private void requireWithinRemaining(
      PropertyPurchaseOrder order, BigDecimal amount, UUID excludingPaymentId, Purpose purpose) {
    List<PurchaseBalancePayment> payments = payments(order);
    BigDecimal inFlight = BigDecimal.ZERO;
    for (PurchaseBalancePayment p : payments) {
      if (p.getPurpose() == purpose
          && (p.getStatus() == Status.PENDING || p.getStatus() == Status.SUBMITTED)
          && !p.getId().equals(excludingPaymentId)) {
        inFlight = inFlight.add(p.getAmount());
      }
    }
    BigDecimal room = remaining(order, payments, purpose).subtract(inFlight);
    if (amount.compareTo(room) > 0) {
      throw new BusinessException(
          room.signum() > 0
              ? "At most "
                  + room.toPlainString()
                  + " "
                  + order.getCurrency().getCode()
                  + " can be paid now (the rest is paid or waiting for confirmation)"
              : "Nothing more is due right now");
    }
  }

  private static BigDecimal sum(
      List<PurchaseBalancePayment> payments, Status status, Purpose purpose) {
    return payments.stream()
        .filter(p -> p.getStatus() == status && p.getPurpose() == purpose)
        .map(PurchaseBalancePayment::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  // ------------------------------------------------------------------ Chapa

  private void settlePending(PropertyPurchaseOrder order) {
    for (PurchaseBalancePayment p : payments(order)) {
      if (p.getStatus() == Status.PENDING && p.getTxRef() != null) {
        try {
          apply(p, chapa.verify(p.getTxRef()));
        } catch (RuntimeException e) {
          log.warn("Could not verify balance payment {}: {}", p.getTxRef(), e.getMessage());
        }
      }
    }
  }

  /** Verify is the source of truth; underpayment and currency mismatch are failures. */
  private void apply(PurchaseBalancePayment p, VerifyResult r) {
    if (r == null) {
      return;
    }
    if (r.isSuccess()) {
      boolean enough =
          r.amount() != null
              && r.amount().compareTo(p.getAmount()) >= 0
              && (r.currency() == null || r.currency().equalsIgnoreCase(p.getCurrency().getCode()));
      if (!enough) {
        p.setStatus(Status.FAILED);
        p.setNote("The provider reported " + r.amount() + " " + r.currency() + ", less than owed");
      } else {
        p.setStatus(Status.PAID);
        p.setPaidAt(LocalDateTime.now());
        p.setProviderReference(r.reference());
        p.setPaymentMethod(r.method());
      }
      paymentRepository.save(p);
    } else if (r.isFailed()) {
      p.setStatus(Status.FAILED);
      p.setNote("The payment was not completed at the provider");
      paymentRepository.save(p);
    }
  }

  /** {@code <order code>-B<n>-<random>} (F for fees), within Chapa Global's 20 characters. */
  static String newTxRef(String orderNumber, String kind, long attempt) {
    String code = orderNumber.substring(orderNumber.lastIndexOf('-') + 1);
    String random =
        UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase(Locale.ROOT);
    String suffix = "-" + kind + attempt + "-" + random;
    int room = PurchaseDepositServiceImpl.MAX_TX_REF_LENGTH - suffix.length();
    return (code.length() > room ? code.substring(0, room) : code) + suffix;
  }

  private String callbackUrl() {
    String base = chapaProperties.getPublicApiBaseUrl();
    if (base == null || base.isBlank()) {
      return null;
    }
    return base.replaceAll("/$", "") + "/api/v1/payments/chapa/webhook";
  }

  private String returnUrl(PropertyPurchaseOrder order) {
    return PurchaseDepositServiceImpl.returnBase(
            frontendBaseUrl, chapaProperties.getPublicApiBaseUrl())
        + "/purchase-orders/"
        + order.getId()
        + "?balance=return";
  }

  // ------------------------------------------------------------------ response

  PurchaseBalanceResponse toResponse(PropertyPurchaseOrder order) {
    List<PurchaseBalancePayment> payments = payments(order);
    BigDecimal due = balanceDue(order);
    BigDecimal paid = sum(payments, Status.PAID, Purpose.BALANCE);
    BigDecimal inProgress =
        sum(payments, Status.PENDING, Purpose.BALANCE)
            .add(sum(payments, Status.SUBMITTED, Purpose.BALANCE));
    BigDecimal remaining = due.subtract(paid).max(BigDecimal.ZERO);

    List<PurchaseBalanceInstalment> saved =
        instalmentRepository.findByPurchaseOrderIdOrderBySequenceAsc(order.getId());
    List<PurchaseBalanceResponse.Instalment> instalments = new ArrayList<>();
    BigDecimal left = paid;
    LocalDate today = LocalDate.now();
    if (saved.isEmpty()) {
      instalments.add(line(null, 1, "Balance", due, null, left, today));
    } else {
      for (PurchaseBalanceInstalment i : saved) {
        instalments.add(
            line(
                i.getId(),
                i.getSequence(),
                i.getLabel(),
                i.getAmount(),
                i.getDueDate(),
                left,
                today));
        left = left.subtract(i.getAmount()).max(BigDecimal.ZERO);
      }
    }

    return PurchaseBalanceResponse.builder()
        .currency(order.getCurrency())
        .listedPrice(order.getListedPrice())
        .depositCredit(depositCredit(order))
        .loanAmount(loan(order))
        .balanceDue(due)
        .paid(paid)
        .inProgress(inProgress)
        .remaining(remaining)
        .fullyPaid(remaining.signum() == 0)
        .payable(PAYABLE.contains(order.getStatus()))
        .checkoutAvailable(chapa.isConfigured())
        .onlineMaxPerPayment(onlineLimits.maxFor(order.getCurrency()))
        .paymentMethods(
            order.getCurrency() == Currency.USD
                ? List.of(ChapaPaymentMethod.CARD)
                : List.of(ChapaPaymentMethod.values()))
        .bankAccount(bankAccount.get())
        .transferReference(order.getOrderNumber() + "-BAL")
        .instalments(instalments)
        .fees(feesOf(order, payments))
        .payments(payments.stream().map(PurchaseBalanceServiceImpl::toPayment).toList())
        .build();
  }

  private static PurchaseBalanceResponse.Fees feesOf(
      PropertyPurchaseOrder order, List<PurchaseBalancePayment> payments) {
    if (order.feesDue().signum() <= 0) {
      return null;
    }
    BigDecimal total = due(order, Purpose.FEES);
    BigDecimal paid = sum(payments, Status.PAID, Purpose.FEES);
    return PurchaseBalanceResponse.Fees.builder()
        .markupPercent(order.getFeeMarkupPercent())
        .markupAmount(order.getFeeMarkupAmount())
        .vatRate(order.getFeeVatRate())
        .vatAmount(order.getFeeVatAmount())
        .total(total)
        .paid(paid)
        .inProgress(
            sum(payments, Status.PENDING, Purpose.FEES)
                .add(sum(payments, Status.SUBMITTED, Purpose.FEES)))
        .remaining(total.subtract(paid).max(BigDecimal.ZERO))
        .fullyPaid(paid.compareTo(total) >= 0)
        .build();
  }

  private static PurchaseBalanceResponse.Instalment line(
      UUID id,
      int seq,
      String label,
      BigDecimal amount,
      LocalDate dueDate,
      BigDecimal paidLeft,
      LocalDate today) {
    BigDecimal covered = paidLeft.min(amount).max(BigDecimal.ZERO);
    boolean paid = covered.compareTo(amount) >= 0;
    return PurchaseBalanceResponse.Instalment.builder()
        .id(id)
        .sequence(seq)
        .label(label)
        .amount(amount)
        .dueDate(dueDate)
        .covered(covered)
        .paid(paid)
        .overdue(!paid && dueDate != null && dueDate.isBefore(today))
        .build();
  }

  private static PurchaseBalanceResponse.Payment toPayment(PurchaseBalancePayment p) {
    return PurchaseBalanceResponse.Payment.builder()
        .id(p.getId())
        .channel(p.getChannel())
        .purpose(p.getPurpose())
        .status(p.getStatus())
        .amount(p.getAmount())
        .currency(p.getCurrency())
        .txRef(p.getTxRef())
        .checkoutUrl(p.getStatus() == Status.PENDING ? p.getCheckoutUrl() : null)
        .preferredMethod(p.getPreferredMethod())
        .paymentMethod(p.getPaymentMethod())
        .reference(p.getReference())
        .paidOn(p.getPaidOn())
        .hasSlip(p.getSlipUrl() != null)
        .slipFileName(p.getSlipFileName())
        .note(p.getNote())
        .paidAt(p.getPaidAt())
        .createdAt(p.getCreatedAt())
        .build();
  }

  // ------------------------------------------------------------------ loading

  private List<PurchaseBalancePayment> payments(PropertyPurchaseOrder order) {
    return paymentRepository.findByPurchaseOrderIdOrderByCreatedAtAsc(order.getId());
  }

  private PropertyPurchaseOrder loadOrder(UUID orderId) {
    return orderRepository
        .findById(orderId)
        .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", orderId));
  }

  private PropertyPurchaseOrder loadOwn(PurchaseOrderActor buyer, UUID orderId) {
    PropertyPurchaseOrder order = loadOrder(orderId);
    if (!PurchaseOrderAccess.isBuyer(buyer, order)) {
      throw new ResourceNotFoundException("PurchaseOrder", orderId);
    }
    return order;
  }

  private PropertyPurchaseOrder loadManaged(PurchaseOrderActor seller, UUID orderId) {
    PropertyPurchaseOrder order = loadOrder(orderId);
    if (!PurchaseOrderAccess.canSell(seller, order)) {
      if (PurchaseOrderAccess.canView(seller, order)) {
        throw new ForbiddenOperationException("Only the listing's agent or company can do this");
      }
      throw new ResourceNotFoundException("PurchaseOrder", orderId);
    }
    return order;
  }

  private PurchaseBalancePayment requirePayment(PropertyPurchaseOrder order, UUID paymentId) {
    return paymentRepository
        .findByIdAndPurchaseOrderId(paymentId, order.getId())
        .orElseThrow(() -> new ResourceNotFoundException("BalancePayment", paymentId));
  }

  private static void requirePayable(PropertyPurchaseOrder order) {
    if (!PAYABLE.contains(order.getStatus())) {
      throw new BusinessException(
          order.getStatus() == PurchaseOrderStatus.PENDING_SELLER_REVIEW
              ? "The balance can be paid once the seller accepts the order"
              : "No balance can be paid on this order now");
    }
  }

  private static void requireOpen(PropertyPurchaseOrder order) {
    if (order.getStatus().isTerminal()) {
      throw new BusinessException("This order is closed");
    }
  }

  private static BigDecimal positive(BigDecimal amount) {
    if (amount == null || amount.signum() <= 0) {
      throw new BusinessException("Enter an amount above zero");
    }
    return amount.setScale(2, RoundingMode.HALF_UP);
  }

  private static String trim(String value, int max) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String t = value.trim();
    return t.length() > max ? t.substring(0, max) : t;
  }

  private static String safeName(String name) {
    if (name == null || name.isBlank()) {
      return "receipt";
    }
    String base = name.replace('\\', '/');
    base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[\\r\\n\"]", "");
    return base.length() > 200 ? base.substring(base.length() - 200) : base;
  }

  /** Shares the document service's content-type sniffing without depending on its bean. */
  static final class PropertyDocumentServiceTypes {
    private PropertyDocumentServiceTypes() {}

    static String normalise(String contentType, String fileName) {
      return com.housingplatform.property.service.impl.PropertyDocumentServiceImpl.normaliseType(
          contentType, fileName);
    }
  }
}
