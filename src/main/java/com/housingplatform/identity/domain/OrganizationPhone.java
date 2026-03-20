package com.housingplatform.identity.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "organization_phones")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class OrganizationPhone extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "contact_id", nullable = false)
  private OrganizationContact contact;

  @Column(name = "country_code", nullable = false, length = 10)
  private String countryCode = "+251";

  @Column(name = "number", nullable = false, length = 50)
  private String number = "";

  @Column(name = "display_order", nullable = false)
  private Integer displayOrder = 0;
}
