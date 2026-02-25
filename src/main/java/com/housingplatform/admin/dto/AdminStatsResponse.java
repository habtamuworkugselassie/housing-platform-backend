package com.housingplatform.admin.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsResponse {
  private Long totalUsers;
  private Long totalOrganizations;
  private Long totalProperties;
  private Long totalBuildings;
  private Long totalProjects;
  private Long totalLoanApplications;
  private Long pendingApprovals;
  private Long activeSponsorships;
  private List<ActivityItem> recentActivity;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ActivityItem {
    private String id;
    private String description;
    private String timestamp;
    private String type;
  }
}
