package com.housingplatform.loan.service;

import com.housingplatform.loan.dto.LoanApplicationRequest;
import com.housingplatform.loan.dto.LoanApplicationResponse;
import com.housingplatform.loan.dto.LoanApprovalRequest;
import com.housingplatform.loan.dto.LoanRejectionRequest;

import java.util.List;
import java.util.UUID;

public interface LoanApplicationService {
    LoanApplicationResponse createLoanApplication(UUID buyerId, LoanApplicationRequest request);
    LoanApplicationResponse getLoanApplicationById(UUID id);
    List<LoanApplicationResponse> getLoanApplicationsByBuyerId(UUID buyerId);
    List<LoanApplicationResponse> getLoanApplicationsByBankId(UUID bankId);
    LoanApplicationResponse updateStatusToUnderReview(UUID bankId, UUID applicationId);
    LoanApplicationResponse approveLoanApplication(UUID bankId, UUID applicationId, LoanApprovalRequest request);
    LoanApplicationResponse rejectLoanApplication(UUID bankId, UUID applicationId, LoanRejectionRequest request);
}
