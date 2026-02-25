package com.housingplatform.loan.api;

import com.housingplatform.identity.service.OrganizationService;
import com.housingplatform.loan.dto.LoanApplicationRequest;
import com.housingplatform.loan.dto.LoanApplicationResponse;
import com.housingplatform.loan.dto.LoanApprovalRequest;
import com.housingplatform.loan.dto.LoanRejectionRequest;
import com.housingplatform.loan.service.LoanApplicationService;
import com.housingplatform.shared.security.annotation.AuthActionScope;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/loan-applications")
@Tag(name = "Loan Applications", description = "Loan application management APIs")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
public class LoanApplicationController {

  private final LoanApplicationService loanApplicationService;
  private final OrganizationService organizationService;

  @PostMapping
  @AuthPolicyScope(AuthPolicyScope.Policy.BUYER_SECURED)
  @AuthActionScope("loan-applications.create")
  @Operation(summary = "Create loan application", description = "Submit a new loan application")
  public ResponseEntity<LoanApplicationResponse> createLoanApplication(
      @Valid @RequestBody LoanApplicationRequest applicationRequest) {
    UUID userId = com.housingplatform.shared.security.UserContext.getCurrentUserId();
    LoanApplicationResponse created =
        loanApplicationService.createLoanApplication(userId, applicationRequest);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Get loan application",
      description = "Retrieve detailed information about a loan application")
  public ResponseEntity<LoanApplicationResponse> getLoanApplication(@PathVariable UUID id) {
    LoanApplicationResponse application = loanApplicationService.getLoanApplicationById(id);
    return ResponseEntity.ok(application);
  }

  @GetMapping("/my-applications")
  @AuthPolicyScope(AuthPolicyScope.Policy.BUYER_SECURED)
  @Operation(
      summary = "Get my loan applications",
      description = "Retrieve all loan applications for the current buyer")
  public ResponseEntity<List<LoanApplicationResponse>> getMyLoanApplications() {
    UUID userId = com.housingplatform.shared.security.UserContext.getCurrentUserId();
    List<LoanApplicationResponse> applications =
        loanApplicationService.getLoanApplicationsByBuyerId(userId);
    return ResponseEntity.ok(applications);
  }

  @GetMapping("/buyers/{buyerId}")
  @Operation(
      summary = "Get buyer's loan applications",
      description = "Retrieve all loan applications for a buyer")
  public ResponseEntity<List<LoanApplicationResponse>> getBuyerLoanApplications(
      @PathVariable UUID buyerId) {
    List<LoanApplicationResponse> applications =
        loanApplicationService.getLoanApplicationsByBuyerId(buyerId);
    return ResponseEntity.ok(applications);
  }

  @GetMapping("/my-bank-applications")
  @AuthPolicyScope(AuthPolicyScope.Policy.BANKER_SECURED)
  @Operation(
      summary = "Get my bank's loan applications",
      description = "Retrieve all loan applications for the current banker's bank")
  public ResponseEntity<List<LoanApplicationResponse>> getMyBankLoanApplications() {
    UUID bankId = organizationService.getMyBank().getId();
    List<LoanApplicationResponse> applications =
        loanApplicationService.getLoanApplicationsByBankId(bankId);
    return ResponseEntity.ok(applications);
  }

  @GetMapping("/banks/{bankId}")
  @Operation(
      summary = "Get bank's loan applications",
      description = "Retrieve all loan applications for a bank")
  public ResponseEntity<List<LoanApplicationResponse>> getBankLoanApplications(
      @PathVariable UUID bankId) {
    List<LoanApplicationResponse> applications =
        loanApplicationService.getLoanApplicationsByBankId(bankId);
    return ResponseEntity.ok(applications);
  }

  @PutMapping("/{id}/review")
  @AuthPolicyScope(AuthPolicyScope.Policy.BANKER_SECURED)
  @AuthActionScope("loan-applications.review")
  @Operation(
      summary = "Move to review",
      description = "Move a loan application to UNDER_REVIEW status (banker only)")
  public ResponseEntity<LoanApplicationResponse> moveToReview(@PathVariable UUID id) {
    UUID bankId = organizationService.getMyBank().getId();
    LoanApplicationResponse updated = loanApplicationService.updateStatusToUnderReview(bankId, id);
    return ResponseEntity.ok(updated);
  }

  @PutMapping("/{id}/approve")
  @AuthPolicyScope(AuthPolicyScope.Policy.BANKER_SECURED)
  @AuthActionScope("loan-applications.approve")
  @Operation(
      summary = "Approve loan application",
      description = "Approve a loan application (banker only)")
  public ResponseEntity<LoanApplicationResponse> approveLoanApplication(
      @PathVariable UUID id, @Valid @RequestBody LoanApprovalRequest approvalRequest) {
    UUID bankId = organizationService.getMyBank().getId();
    LoanApplicationResponse updated =
        loanApplicationService.approveLoanApplication(bankId, id, approvalRequest);
    return ResponseEntity.ok(updated);
  }

  @PutMapping("/{id}/reject")
  @AuthPolicyScope(AuthPolicyScope.Policy.BANKER_SECURED)
  @AuthActionScope("loan-applications.reject")
  @Operation(
      summary = "Reject loan application",
      description = "Reject a loan application (banker only)")
  public ResponseEntity<LoanApplicationResponse> rejectLoanApplication(
      @PathVariable UUID id, @Valid @RequestBody LoanRejectionRequest rejectionRequest) {
    UUID bankId = organizationService.getMyBank().getId();
    LoanApplicationResponse updated =
        loanApplicationService.rejectLoanApplication(bankId, id, rejectionRequest);
    return ResponseEntity.ok(updated);
  }
}
