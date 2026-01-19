package com.housingplatform.loan.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "loan_application_status_history")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class LoanApplicationStatusHistory extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_application_id", nullable = false)
    private LoanApplication loanApplication;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanApplication.LoanApplicationStatus fromStatus;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanApplication.LoanApplicationStatus toStatus;
    
    @Column(columnDefinition = "TEXT")
    private String notes;
    
    @Column(nullable = false)
    private String changedBy;
    
    @Column(nullable = false)
    private LocalDateTime changedAt;
}
