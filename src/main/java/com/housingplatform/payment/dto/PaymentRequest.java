package com.housingplatform.payment.dto;

import com.housingplatform.payment.domain.Payment;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class PaymentRequest {
    
    @NotNull(message = "Payment type is required")
    private Payment.PaymentType type;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;
    
    @NotBlank(message = "Currency is required")
    private String currency;
    
    @NotNull(message = "Payee ID is required")
    private UUID payeeId;
    
    private UUID loanApplicationId;
    private UUID orderId;
    private String description;
}
