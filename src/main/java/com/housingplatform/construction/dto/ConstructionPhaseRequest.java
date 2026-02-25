package com.housingplatform.construction.dto;

import com.housingplatform.construction.domain.ConstructionPhase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Data;

@Data
public class ConstructionPhaseRequest {

  @NotNull(message = "Project ID is required")
  private UUID projectId;

  @NotBlank(message = "Phase name is required")
  private String name;

  private String description;

  @NotNull(message = "Phase type is required")
  private ConstructionPhase.PhaseType type;

  private LocalDate startDate;

  private LocalDate plannedEndDate;

  private BigDecimal budget;

  @NotNull(message = "Sequence is required")
  private Integer sequence;

  private String notes;
}
