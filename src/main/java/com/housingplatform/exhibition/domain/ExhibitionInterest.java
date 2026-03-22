package com.housingplatform.exhibition.domain;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.domain.Sponsorship;
import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "exhibition_interest")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ExhibitionInterest extends BaseEntity {

  @Column(nullable = false)
  private String email;

  @Column(name = "phone_number", length = 50)
  private String phoneNumber;

  @Column(name = "interest_type", nullable = false, length = 50)
  private String interestType; // "exhibitor" | "visitor"

  @Column(length = 500)
  private String company;

  @Column(columnDefinition = "TEXT")
  private String message;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id")
  private Organization organization;

  /** When interest is exhibitor: which sponsorship tier they are interested in (optional FK). */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sponsorship_id")
  private Sponsorship sponsorship;
}
