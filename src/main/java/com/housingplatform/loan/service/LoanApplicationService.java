package com.housingplatform.loan.service;

import com.housingplatform.loan.dto.LoanApplicationRequest;
import com.housingplatform.loan.dto.LoanApplicationResponse;
import com.housingplatform.loan.dto.LoanApprovalRequest;
import com.housingplatform.loan.dto.LoanRejectionRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LoanApplicationService {
  LoanApplicationResponse createLoanApplication(UUID buyerId, LoanApplicationRequest request);

  LoanApplicationResponse getLoanApplicationById(UUID id);

  List<LoanApplicationResponse> getLoanApplicationsByBuyerId(UUID buyerId);

  List<LoanApplicationResponse> getLoanApplicationsByBankId(UUID bankId);

  LoanApplicationResponse updateStatusToUnderReview(UUID bankId, UUID applicationId);

  LoanApplicationResponse approveLoanApplication(
      UUID bankId, UUID applicationId, LoanApprovalRequest request);

  LoanApplicationResponse rejectLoanApplication(
      UUID bankId, UUID applicationId, LoanRejectionRequest request);

  /**
   * Changes the amount and/or tenure of an application the bank has not started reviewing yet
   * (status {@code SUBMITTED}). Null arguments leave the corresponding field untouched.
   */
  LoanApplicationResponse updateRequestedTerms(
      UUID applicationId, BigDecimal requestedAmount, Integer requestedTenureMonths);

  /**
   * Closes an application the applicant no longer needs (for example the purchase it was meant to
   * finance was cancelled). Allowed from {@code SUBMITTED} and {@code UNDER_REVIEW}; any other
   * status is left untouched and returned as is.
   */
  LoanApplicationResponse withdrawLoanApplication(UUID applicationId, String reason);
}
