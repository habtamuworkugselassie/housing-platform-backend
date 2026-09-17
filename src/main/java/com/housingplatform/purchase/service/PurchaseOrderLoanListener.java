package com.housingplatform.purchase.service;

import com.housingplatform.loan.service.LoanApplicationEvents.LoanApplicationStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Keeps a financed purchase order in step with the bank's decisions on its loan application. */
@Component
@RequiredArgsConstructor
public class PurchaseOrderLoanListener {

  private final PurchaseOrderService purchaseOrderService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onLoanStatusChanged(LoanApplicationStatusChangedEvent event) {
    purchaseOrderService.applyLoanApplicationStatus(event.loanApplicationId(), event.toStatus());
  }
}
