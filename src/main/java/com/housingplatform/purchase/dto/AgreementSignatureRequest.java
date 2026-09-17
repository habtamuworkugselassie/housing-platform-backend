package com.housingplatform.purchase.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Data;

/** The buyer's electronic signature on one agreement. */
@Data
public class AgreementSignatureRequest {

  /**
   * The template (id + version) the buyer read, as returned by the preview or the agreement itself.
   */
  @NotNull(message = "Agreement template ID is required")
  private UUID templateId;

  @AssertTrue(message = "The agreement must be accepted")
  private boolean accepted;

  @NotBlank(message = "Signatory full name is required")
  @Size(max = 255)
  private String signatoryFullName;
}
