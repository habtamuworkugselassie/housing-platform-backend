package com.housingplatform.construction.dto;

import com.housingplatform.construction.domain.ConstructionProject;
import com.housingplatform.shared.domain.Currency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Data;

@Data
public class ConstructionProjectRequest {

  @NotBlank(message = "Project name is required")
  private String name;

  private String description;

  private UUID propertyId;

  private UUID buildingId;

  @NotNull(message = "Real estate company ID is required")
  private UUID realEstateCompanyId;

  private UUID projectManagerId;

  @NotNull(message = "Project type is required")
  private ConstructionProject.ProjectType type;

  private LocalDate startDate;

  private LocalDate plannedEndDate;

  private BigDecimal budget;

  @NotNull(message = "Currency is required")
  private Currency currency;

  private String locationAddress;

  private String locationCity;

  private String locationState;

  private String locationCountry;
}
