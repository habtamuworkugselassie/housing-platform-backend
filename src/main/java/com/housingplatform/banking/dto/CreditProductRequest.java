package com.housingplatform.banking.dto;

import com.housingplatform.banking.domain.CreditProduct;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreditProductRequest {
    
    @NotBlank(message = "Product name is required")
    private String name;
    
    private String description;
    
    @NotNull(message = "Product type is required")
    private CreditProduct.CreditProductType productType;
    
    @NotNull(message = "Interest rate is required")
    @DecimalMin(value = "0.0", message = "Interest rate must be non-negative")
    @DecimalMax(value = "100.0", message = "Interest rate cannot exceed 100%")
    private BigDecimal interestRate;
    
    @NotNull(message = "Minimum tenure is required")
    @Min(value = 1, message = "Minimum tenure must be at least 1 month")
    private Integer minTenureMonths;
    
    @NotNull(message = "Maximum tenure is required")
    @Min(value = 1, message = "Maximum tenure must be at least 1 month")
    private Integer maxTenureMonths;
    
    @NotNull(message = "Maximum LTV ratio is required")
    @DecimalMin(value = "0.0", message = "LTV ratio must be non-negative")
    @DecimalMax(value = "1.0", message = "LTV ratio cannot exceed 1.0")
    private BigDecimal maxLoanToValueRatio;
    
    @NotNull(message = "Minimum loan amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Minimum loan amount must be greater than 0")
    private BigDecimal minLoanAmount;
    
    @NotNull(message = "Maximum loan amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Maximum loan amount must be greater than 0")
    private BigDecimal maxLoanAmount;
    
    private com.housingplatform.shared.domain.Currency currency;
    
    private String eligibilityCriteria;
    
    private BigDecimal processingFee;
    private BigDecimal prepaymentPenalty;
}
