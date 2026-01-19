package com.housingplatform.payment.repository;

import com.housingplatform.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {
    Optional<Payment> findByPaymentReference(String paymentReference);
    Optional<Payment> findByTransactionReference(String transactionReference);
    List<Payment> findByLoanApplicationId(UUID loanApplicationId);
    List<Payment> findByOrderId(UUID orderId);
    List<Payment> findByStatus(Payment.PaymentStatus status);
    List<Payment> findByPayerId(UUID payerId);
    List<Payment> findByPayeeId(UUID payeeId);
}
