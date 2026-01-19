package com.housingplatform.payment.service;

import com.housingplatform.payment.dto.LoanDisbursementRequest;
import com.housingplatform.payment.dto.PaymentRequest;
import com.housingplatform.payment.dto.PaymentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PaymentService {
    PaymentResponse createPayment(UUID payerId, PaymentRequest request);
    PaymentResponse getPaymentById(UUID id);
    Page<PaymentResponse> getAllPayments(UUID loanApplicationId, UUID orderId, String status, Pageable pageable);
    PaymentResponse disburseLoan(UUID bankId, LoanDisbursementRequest request);
    PaymentResponse getPaymentStatus(UUID id);
}
