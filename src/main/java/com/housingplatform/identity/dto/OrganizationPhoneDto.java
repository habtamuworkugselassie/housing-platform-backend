package com.housingplatform.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationPhoneDto {

  @NotBlank(message = "Country code is required")
  @Size(max = 10)
  private String countryCode;

  @Size(max = 50)
  private String
      number; // optional for "add another" row; entries with blank number are skipped when saving
}
