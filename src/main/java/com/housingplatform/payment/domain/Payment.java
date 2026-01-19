package com.housingplatform.payment.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Payment extends BaseAuditEntity {
    
    @Column(name = "loan_application_id")
    private UUID loanApplicationId; // Optional: for loan disbursements
    
    @Column(name = "order_id")
    private UUID orderId; // Optional: for material orders
    
    @Column(nullable = false)
    private String paymentReference;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType type;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private com.housingplatform.shared.domain.Currency currency = com.housingplatform.shared.domain.Currency.ETB;
    
    @Column(name = "payer_id", nullable = false)
    private UUID payerId;
    
    @Column(name = "payee_id", nullable = false)
    private UUID payeeId;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(columnDefinition = "TEXT")
    private String transactionReference; // External payment gateway reference
    
    @Column(columnDefinition = "TEXT")
    private String failureReason;
    
    public enum PaymentType {
        LOAN_DISBURSEMENT, MATERIAL_PURCHASE, ESCROW, REFUND
    }
    
    public enum PaymentStatus {
        PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED
    }
}
