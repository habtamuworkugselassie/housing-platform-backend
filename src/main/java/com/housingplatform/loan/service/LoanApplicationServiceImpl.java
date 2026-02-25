package com.housingplatform.loan.service;

import com.housingplatform.loan.domain.LoanApplication;
import com.housingplatform.loan.domain.LoanApplicationStatusHistory;
import com.housingplatform.loan.dto.LoanApplicationRequest;
import com.housingplatform.loan.dto.LoanApplicationResponse;
import com.housingplatform.loan.dto.LoanApprovalRequest;
import com.housingplatform.loan.dto.LoanRejectionRequest;
import com.housingplatform.loan.repository.LoanApplicationRepository;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class LoanApplicationServiceImpl implements LoanApplicationService {

  private final LoanApplicationRepository loanApplicationRepository;
  private final LoanApplicationMapper loanApplicationMapper;

  @Override
  public LoanApplicationResponse createLoanApplication(
      UUID buyerId, LoanApplicationRequest request) {
    LoanApplication application = loanApplicationMapper.toEntity(request);
    application.setBuyerId(buyerId);
    application.setStatus(LoanApplication.LoanApplicationStatus.SUBMITTED);

    // Add status history
    LoanApplicationStatusHistory history =
        LoanApplicationStatusHistory.builder()
            .loanApplication(application)
            .fromStatus(LoanApplication.LoanApplicationStatus.DRAFT)
            .toStatus(LoanApplication.LoanApplicationStatus.SUBMITTED)
            .changedBy(buyerId.toString())
            .changedAt(LocalDateTime.now())
            .build();
    application.getStatusHistory().add(history);

    LoanApplication saved = loanApplicationRepository.save(application);
    return loanApplicationMapper.toResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public LoanApplicationResponse getLoanApplicationById(UUID id) {
    LoanApplication application =
        loanApplicationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("LoanApplication", id));
    return loanApplicationMapper.toResponse(application);
  }

  @Override
  @Transactional(readOnly = true)
  public List<LoanApplicationResponse> getLoanApplicationsByBuyerId(UUID buyerId) {
    return loanApplicationRepository.findByBuyerId(buyerId).stream()
        .map(loanApplicationMapper::toResponse)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<LoanApplicationResponse> getLoanApplicationsByBankId(UUID bankId) {
    return loanApplicationRepository.findByBankId(bankId).stream()
        .map(loanApplicationMapper::toResponse)
        .collect(Collectors.toList());
  }

  @Override
  public LoanApplicationResponse updateStatusToUnderReview(UUID bankId, UUID applicationId) {
    LoanApplication application =
        loanApplicationRepository
            .findById(applicationId)
            .orElseThrow(() -> new ResourceNotFoundException("LoanApplication", applicationId));

    if (!application.getBankId().equals(bankId)) {
      throw new IllegalArgumentException("Loan application does not belong to the specified bank");
    }

    if (application.getStatus() != LoanApplication.LoanApplicationStatus.SUBMITTED) {
      throw new BusinessException(
          "Loan application must be in SUBMITTED status to move to UNDER_REVIEW");
    }

    application.setStatus(LoanApplication.LoanApplicationStatus.UNDER_REVIEW);

    // Add status history
    LoanApplicationStatusHistory history =
        LoanApplicationStatusHistory.builder()
            .loanApplication(application)
            .fromStatus(LoanApplication.LoanApplicationStatus.SUBMITTED)
            .toStatus(LoanApplication.LoanApplicationStatus.UNDER_REVIEW)
            .changedBy(bankId.toString())
            .changedAt(LocalDateTime.now())
            .notes("Application moved to review")
            .build();
    application.getStatusHistory().add(history);

    LoanApplication saved = loanApplicationRepository.save(application);
    return loanApplicationMapper.toResponse(saved);
  }

  @Override
  public LoanApplicationResponse approveLoanApplication(
      UUID bankId, UUID applicationId, LoanApprovalRequest request) {
    LoanApplication application =
        loanApplicationRepository
            .findById(applicationId)
            .orElseThrow(() -> new ResourceNotFoundException("LoanApplication", applicationId));

    if (!application.getBankId().equals(bankId)) {
      throw new IllegalArgumentException("Loan application does not belong to the specified bank");
    }

    if (application.getStatus() != LoanApplication.LoanApplicationStatus.UNDER_REVIEW) {
      throw new BusinessException("Loan application must be in UNDER_REVIEW status to be approved");
    }

    application.setStatus(LoanApplication.LoanApplicationStatus.APPROVED);
    application.setApprovedAmount(request.getApprovedAmount());
    application.setApprovedCurrency(
        request.getApprovedCurrency() != null
            ? request.getApprovedCurrency()
            : application.getCurrency());
    application.setApprovedInterestRate(request.getApprovedInterestRate());
    application.setApprovedTenureMonths(request.getApprovedTenureMonths());
    application.setApprovalNotes(request.getApprovalNotes());

    // Add status history
    LoanApplicationStatusHistory history =
        LoanApplicationStatusHistory.builder()
            .loanApplication(application)
            .fromStatus(LoanApplication.LoanApplicationStatus.UNDER_REVIEW)
            .toStatus(LoanApplication.LoanApplicationStatus.APPROVED)
            .changedBy(bankId.toString())
            .changedAt(LocalDateTime.now())
            .notes(request.getApprovalNotes())
            .build();
    application.getStatusHistory().add(history);

    LoanApplication saved = loanApplicationRepository.save(application);
    return loanApplicationMapper.toResponse(saved);
  }

  @Override
  public LoanApplicationResponse rejectLoanApplication(
      UUID bankId, UUID applicationId, LoanRejectionRequest request) {
    LoanApplication application =
        loanApplicationRepository
            .findById(applicationId)
            .orElseThrow(() -> new ResourceNotFoundException("LoanApplication", applicationId));

    if (!application.getBankId().equals(bankId)) {
      throw new IllegalArgumentException("Loan application does not belong to the specified bank");
    }

    if (application.getStatus() != LoanApplication.LoanApplicationStatus.UNDER_REVIEW) {
      throw new BusinessException("Loan application must be in UNDER_REVIEW status to be rejected");
    }

    application.setStatus(LoanApplication.LoanApplicationStatus.REJECTED);
    application.setRejectionReason(request.getRejectionReason());

    // Add status history
    LoanApplicationStatusHistory history =
        LoanApplicationStatusHistory.builder()
            .loanApplication(application)
            .fromStatus(LoanApplication.LoanApplicationStatus.UNDER_REVIEW)
            .toStatus(LoanApplication.LoanApplicationStatus.REJECTED)
            .changedBy(bankId.toString())
            .changedAt(LocalDateTime.now())
            .notes(request.getRejectionReason())
            .build();
    application.getStatusHistory().add(history);

    LoanApplication saved = loanApplicationRepository.save(application);
    return loanApplicationMapper.toResponse(saved);
  }
}
