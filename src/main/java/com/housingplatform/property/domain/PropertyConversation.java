package com.housingplatform.property.domain;

import com.housingplatform.identity.domain.RealEstateAgent;
import com.housingplatform.identity.domain.User;
import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "property_conversations",
    uniqueConstraints = @UniqueConstraint(columnNames = {"buyer_user_id", "property_id"}))
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PropertyConversation extends BaseAuditEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "buyer_user_id", nullable = false)
  private User buyer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "agent_id", nullable = false)
  private RealEstateAgent agent;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "property_id", nullable = false)
  private Property property;
}
