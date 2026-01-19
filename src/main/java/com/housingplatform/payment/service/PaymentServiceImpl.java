package com.housingplatform.payment.service;

import com.housingplatform.payment.domain.Payment;
import com.housingplatform.payment.dto.LoanDisbursementRequest;
import com.housingplatform.payment.dto.PaymentRequest;
import com.housingplatform.payment.dto.PaymentResponse;
import com.housingplatform.payment.repository.PaymentRepository;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentServiceImpl implements PaymentService {
    
    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    
    @Override
    public PaymentResponse createPayment(UUID payerId, PaymentRequest request) {
        Payment payment = paymentMapper.toEntity(request);
        payment.setPayerId(payerId);
        payment.setStatus(Payment.PaymentStatus.PENDING);
        payment.setPaymentReference(generatePaymentReference());
        Payment saved = paymentRepository.save(payment);
        return paymentMapper.toResponse(saved);
    }
    
    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        return paymentMapper.toResponse(payment);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getAllPayments(UUID loanApplicationId, UUID orderId, String status, Pageable pageable) {
        Specification<Payment> spec = Specification.where(null);
        
        if (loanApplicationId != null) {
            spec = spec.and((root, query, cb) -> 
                cb.equal(root.get("loanApplicationId"), loanApplicationId));
        }
        
        if (orderId != null) {
            spec = spec.and((root, query, cb) -> 
                cb.equal(root.get("orderId"), orderId));
        }
        
        if (status != null) {
            spec = spec.and((root, query, cb) -> 
                cb.equal(root.get("status"), Payment.PaymentStatus.valueOf(status.toUpperCase())));
        }
        
        return paymentRepository.findAll(spec, pageable)
                .map(paymentMapper::toResponse);
    }
    
    @Override
    public PaymentResponse disburseLoan(UUID bankId, LoanDisbursementRequest request) {
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setType(Payment.PaymentType.LOAN_DISBURSEMENT);
        paymentRequest.setAmount(request.getAmount());
        paymentRequest.setCurrency(request.getCurrency());
        paymentRequest.setLoanApplicationId(request.getLoanApplicationId());
        paymentRequest.setDescription(request.getDescription());
        
        // For loan disbursement, bank is the payer
        PaymentResponse payment = createPayment(bankId, paymentRequest);
        
        // Update payment status to processing
        Payment paymentEntity = paymentRepository.findById(payment.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", payment.getId()));
        paymentEntity.setStatus(Payment.PaymentStatus.PROCESSING);
        Payment updated = paymentRepository.save(paymentEntity);
        
        return paymentMapper.toResponse(updated);
    }
    
    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentStatus(UUID id) {
        return getPaymentById(id);
    }
    
    private String generatePaymentReference() {
        return "PAY-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
