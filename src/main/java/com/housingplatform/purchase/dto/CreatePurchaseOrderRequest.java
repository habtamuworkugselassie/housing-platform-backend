package com.housingplatform.purchase.dto;

import com.housingplatform.shared.domain.Currency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;

@Data
public class CreatePurchaseOrderRequest {

  @NotNull(message = "Property ID is required")
  private UUID propertyId;

  @NotBlank(message = "Contact phone number is required")
  @Size(max = 32, message = "Contact phone number is too long")
  private String contactPhone;

  @Email(message = "Contact email must be a valid email address")
  @Size(max = 255, message = "Contact email is too long")
  private String contactEmail;

  /** Defaults to ETB. The property must carry a price in this currency. */
  private Currency currency;

  @Size(max = 2000, message = "Message cannot exceed 2000 characters")
  private String buyerMessage;

  /**
   * null: financed automatically when the property has an active financing product, cash otherwise.
   * false: cash even if financing exists. true: financing required, 400 if none exists.
   */
  private Boolean useFinancing;

  /** Only consulted when the order ends up bank financed. */
  @Valid private FinancingSelection financing;

  @Data
  public static class FinancingSelection {
    /**
     * Required only when more than one eligible offer exists and the buyer wants a specific one.
     */
    private UUID financingOfferId;

    /** Partial financing: the loan principal wanted. Mutually exclusive with downPaymentAmount. */
    @DecimalMin(value = "0.01", message = "Financed amount must be greater than 0")
    private BigDecimal financedAmount;

    /**
     * Partial financing expressed as the cash the buyer pays. Mutually exclusive with
     * financedAmount.
     */
    @DecimalMin(value = "0.00", message = "Down payment cannot be negative")
    private BigDecimal downPaymentAmount;

    @Min(value = 1, message = "Requested tenure must be at least 1 month")
    private Integer requestedTenureMonths;
  }
}
