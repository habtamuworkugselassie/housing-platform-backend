package com.housingplatform.construction.dto;

import com.housingplatform.construction.domain.ConstructionPhase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;

@Data
public class ConstructionPhaseResponse {

  private UUID id;

  private UUID projectId;

  private String projectName;

  private String name;

  private String description;

  private ConstructionPhase.PhaseStatus status;

  private ConstructionPhase.PhaseType type;

  private LocalDate startDate;

  private LocalDate plannedEndDate;

  private LocalDate actualEndDate;

  private Integer completionPercentage;

  private BigDecimal budget;

  private BigDecimal actualCost;

  private Integer sequence;

  private String notes;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;
}
