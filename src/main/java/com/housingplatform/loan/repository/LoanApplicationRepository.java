package com.housingplatform.loan.repository;

import com.housingplatform.loan.domain.LoanApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {
    List<LoanApplication> findByBuyerId(UUID buyerId);
    List<LoanApplication> findByBankId(UUID bankId);
    List<LoanApplication> findByPropertyId(UUID propertyId);
    List<LoanApplication> findByStatus(LoanApplication.LoanApplicationStatus status);
    List<LoanApplication> findByBuyerIdAndStatus(UUID buyerId, LoanApplication.LoanApplicationStatus status);
    List<LoanApplication> findByBankIdAndStatus(UUID bankId, LoanApplication.LoanApplicationStatus status);
}
