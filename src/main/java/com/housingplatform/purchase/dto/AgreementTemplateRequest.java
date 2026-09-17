package com.housingplatform.purchase.dto;

import com.housingplatform.purchase.domain.AgreementTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Creates a new version of a template. Existing versions are immutable. */
@Data
public class AgreementTemplateRequest {
  @NotNull private AgreementTemplate.AgreementType type;

  @NotBlank
  @Size(max = 255)
  private String title;

  @NotBlank private String body;

  @NotNull private AgreementTemplate.IssueTrigger issueTrigger;

  private AgreementTemplate.AppliesTo appliesTo;
  private Integer sequence;
  private Boolean blocksCompletion;

  /** Activate immediately, deactivating the previous active version of the same type. */
  private Boolean activate;
}
