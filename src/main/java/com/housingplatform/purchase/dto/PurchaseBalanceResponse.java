package com.housingplatform.purchase.dto;

import com.housingplatform.payment.chapa.ChapaPaymentMethod;
import com.housingplatform.purchase.domain.PurchaseBalancePayment;
import com.housingplatform.shared.domain.Currency;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/** What the buyer still owes the provider on an order, the schedule, and what has been paid. */
@Data
@Builder
public class PurchaseBalanceResponse {
  private Currency currency;
  private BigDecimal listedPrice;

  /** The reservation deposit counted towards the price (in the order currency). */
  private BigDecimal depositCredit;

  /** The bank loan's share, paid by the bank rather than the buyer. */
  private BigDecimal loanAmount;

  /** Price - deposit credit - loan. */
  private BigDecimal balanceDue;

  private BigDecimal paid;

  /** Online checkouts and bank transfers waiting for confirmation. */
  private BigDecimal inProgress;

  private BigDecimal remaining;
  private boolean fullyPaid;

  /** True once the seller has accepted and the order is still open. */
  private boolean payable;

  private boolean checkoutAvailable;

  /** The most one online payment may be (Chapa's limit); null when none is set. */
  private BigDecimal onlineMaxPerPayment;

  private List<ChapaPaymentMethod> paymentMethods;

  /** Where to send a bank transfer, and the reference to quote; null until an admin sets it. */
  private BankAccount bankAccount;

  private String transferReference;
  private List<Instalment> instalments;
  private List<Payment> payments;

  /** The provider's service fee (markup + VAT), paid separately; null when none applies. */
  private Fees fees;

  @Data
  @Builder
  public static class Fees {
    private BigDecimal markupPercent;
    private BigDecimal markupAmount;
    private BigDecimal vatRate;
    private BigDecimal vatAmount;
    private BigDecimal total;
    private BigDecimal paid;
    private BigDecimal inProgress;
    private BigDecimal remaining;
    private boolean fullyPaid;
  }

  @Data
  @Builder
  public static class BankAccount {
    private String bankName;
    private String accountName;
    private String accountNumber;
    private String branch;
  }

  @Data
  @Builder
  public static class Instalment {
    private UUID id;
    private Integer sequence;
    private String label;
    private BigDecimal amount;
    private LocalDate dueDate;

    /** How much of this line the payments made so far cover (they count in schedule order). */
    private BigDecimal covered;

    private boolean paid;
    private boolean overdue;
  }

  @Data
  @Builder
  public static class Payment {
    private UUID id;
    private PurchaseBalancePayment.Channel channel;
    private PurchaseBalancePayment.Purpose purpose;
    private PurchaseBalancePayment.Status status;
    private BigDecimal amount;
    private Currency currency;
    private String txRef;
    private String checkoutUrl;
    private ChapaPaymentMethod preferredMethod;
    private String paymentMethod;
    private String reference;
    private LocalDate paidOn;
    private boolean hasSlip;
    private String slipFileName;
    private String note;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
  }
}
