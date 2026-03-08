package com.housingplatform.property.domain;

import com.housingplatform.identity.domain.User;
import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Review extends BaseAuditEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "property_id", nullable = false)
  private Property property;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false)
  private Integer rating;

  @Column(columnDefinition = "TEXT")
  private String comment;
}
