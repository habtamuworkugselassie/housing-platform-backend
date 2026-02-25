package com.housingplatform.identity.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "real_estate_agents",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"user_id"}),
      @UniqueConstraint(columnNames = {"organization_id", "user_id"})
    })
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class RealEstateAgent extends BaseAuditEntity {

  @OneToOne
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @ManyToOne
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private AgentStatus status = AgentStatus.ACTIVE;

  @Column(columnDefinition = "TEXT")
  private String licenseNumber;

  @Column(columnDefinition = "TEXT")
  private String notes;

  @Column(nullable = false)
  @Builder.Default
  private Boolean isSuperAgent = false;

  public enum AgentStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED
  }

  public UUID getUserId() {
    return user != null ? user.getId() : null;
  }

  public UUID getOrganizationId() {
    return organization != null ? organization.getId() : null;
  }
}
