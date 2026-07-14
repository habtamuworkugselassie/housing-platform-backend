package com.housingplatform.property.domain;

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
    name = "favorite_properties",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "property_id"}))
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class FavoriteProperty extends BaseAuditEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "property_id", nullable = false)
  private Property property;
}
