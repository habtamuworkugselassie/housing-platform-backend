package com.housingplatform.exhibition.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExhibitionInterestResponse {

  private UUID id;
  private String email;
  private String phoneNumber;
  private String interestType;
  private String company;
  private String message;
  private UUID organizationId;
}
