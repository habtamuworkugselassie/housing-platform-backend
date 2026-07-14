package com.housingplatform.property.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreatePropertyMessageRequest {

  @NotBlank
  @Size(max = 4000)
  private String message;
}
