package com.housingplatform.payment.dto;

import com.housingplatform.payment.domain.Payment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private UUID id;
    private UUID loanApplicationId;
    private UUID orderId;
    private String paymentReference;
    private Payment.PaymentType type;
    private Payment.PaymentStatus status;
    private BigDecimal amount;
    private String currency;
    private UUID payerId;
    private UUID payeeId;
    private String description;
    private String transactionReference;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
