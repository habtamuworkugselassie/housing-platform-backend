package com.housingplatform.loan.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "loan_documents")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class LoanDocument extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_application_id", nullable = false)
    private LoanApplication loanApplication;
    
    @Column(nullable = false)
    private String documentType; // e.g., INCOME_PROOF, IDENTITY, PROPERTY_DOCS
    
    @Column(nullable = false)
    private String fileName;
    
    @Column(nullable = false)
    private String fileUrl;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentVerificationStatus verificationStatus;
    
    public enum DocumentVerificationStatus {
        PENDING, VERIFIED, REJECTED
    }
}
