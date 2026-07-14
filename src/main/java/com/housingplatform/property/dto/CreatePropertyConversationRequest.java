package com.housingplatform.property.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Data;

@Data
public class CreatePropertyConversationRequest {

  @NotNull private UUID propertyId;

  @NotBlank
  @Size(max = 4000)
  private String message;
}
