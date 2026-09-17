package com.housingplatform.purchase.dto;

import com.housingplatform.purchase.domain.AgreementTemplate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgreementTemplateResponse {
  private UUID id;
  private AgreementTemplate.AgreementType type;
  private Integer version;
  private String title;
  private String body;
  private AgreementTemplate.IssueTrigger issueTrigger;
  private AgreementTemplate.AppliesTo appliesTo;
  private Integer sequence;
  private Boolean blocksCompletion;
  private Boolean active;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
