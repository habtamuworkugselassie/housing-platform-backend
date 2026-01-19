package com.housingplatform.payment.api;

import com.housingplatform.payment.dto.LoanDisbursementRequest;
import com.housingplatform.payment.dto.PaymentRequest;
import com.housingplatform.payment.dto.PaymentResponse;
import com.housingplatform.payment.service.PaymentService;
import com.housingplatform.shared.security.annotation.AuthActionScope;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import com.housingplatform.shared.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Payment management APIs")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
public class PaymentController {
    
    private final PaymentService paymentService;
    
    @GetMapping
    @Operation(summary = "List payments", description = "Retrieve all payments with optional filtering")
    public ResponseEntity<Page<PaymentResponse>> getAllPayments(
            @RequestParam(required = false) UUID loanApplicationId,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PaymentResponse> payments = paymentService.getAllPayments(loanApplicationId, orderId, status, pageable);
        return ResponseEntity.ok(payments);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get payment by ID", description = "Retrieve payment information by ID")
    public ResponseEntity<PaymentResponse> getPaymentById(@PathVariable UUID id) {
        PaymentResponse payment = paymentService.getPaymentById(id);
        return ResponseEntity.ok(payment);
    }
    
    @PostMapping
    @Operation(summary = "Create payment", description = "Create a new payment")
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody PaymentRequest paymentRequest) {
        UUID payerId = UserContext.getCurrentUserId();
        PaymentResponse created = paymentService.createPayment(payerId, paymentRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @PostMapping("/disburse")
    @AuthPolicyScope(AuthPolicyScope.Policy.BANKER_SECURED)
    @AuthActionScope("payments.disburse")
    @Operation(summary = "Disburse loan", description = "Create a loan disbursement payment (banker only)")
    public ResponseEntity<PaymentResponse> disburseLoan(@Valid @RequestBody LoanDisbursementRequest disbursementRequest) {
        UUID bankId = UserContext.getCurrentUserId();
        PaymentResponse payment = paymentService.disburseLoan(bankId, disbursementRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }
    
    @GetMapping("/{id}/status")
    @Operation(summary = "Get payment status", description = "Retrieve current status of a payment")
    public ResponseEntity<PaymentResponse> getPaymentStatus(@PathVariable UUID id) {
        PaymentResponse payment = paymentService.getPaymentStatus(id);
        return ResponseEntity.ok(payment);
    }
}
