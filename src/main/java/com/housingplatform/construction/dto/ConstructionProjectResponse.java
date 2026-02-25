package com.housingplatform.construction.dto;

import com.housingplatform.construction.domain.ConstructionProject;
import com.housingplatform.shared.domain.Currency;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class ConstructionProjectResponse {

  private UUID id;

  private String name;

  private String description;

  private UUID propertyId;

  private UUID buildingId;

  private UUID realEstateCompanyId;

  private String realEstateCompanyName;

  private UUID projectManagerId;

  private String projectManagerName;

  private ConstructionProject.ProjectStatus status;

  private ConstructionProject.ProjectType type;

  private LocalDate startDate;

  private LocalDate plannedEndDate;

  private LocalDate actualEndDate;

  private BigDecimal budget;

  private BigDecimal totalCost;

  private Currency currency;

  private String locationAddress;

  private String locationCity;

  private String locationState;

  private String locationCountry;

  private List<ConstructionPhaseResponse> phases;

  private Integer totalPhases;

  private Integer completedPhases;

  private Integer overallCompletionPercentage;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;
}
