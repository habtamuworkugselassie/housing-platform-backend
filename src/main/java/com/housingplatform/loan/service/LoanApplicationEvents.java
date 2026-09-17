package com.housingplatform.loan.service;

import com.housingplatform.loan.domain.LoanApplication;
import java.util.UUID;

/** In-process events raised by the loan module so other modules can follow a loan's progress. */
public final class LoanApplicationEvents {
  private LoanApplicationEvents() {}

  /** Published after every status transition of a {@link LoanApplication}. */
  public record LoanApplicationStatusChangedEvent(
      UUID loanApplicationId,
      LoanApplication.LoanApplicationStatus fromStatus,
      LoanApplication.LoanApplicationStatus toStatus) {}
}
