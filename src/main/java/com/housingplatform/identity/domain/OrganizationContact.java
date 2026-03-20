package com.housingplatform.identity.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Consolidates organization reachability: email, website, social profiles, and phone lines. One row
 * per organization (1:1); phones reference this aggregate.
 */
@Entity
@Table(name = "organization_contacts")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class OrganizationContact extends BaseEntity {

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id", nullable = false, unique = true)
  private Organization organization;

  private String email;
  private String website;

  private String facebookUrl;
  private String instagramUrl;
  private String linkedinUrl;
  private String twitterUrl;
  private String youtubeUrl;

  @OneToMany(
      mappedBy = "contact",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("displayOrder ASC")
  @Builder.Default
  private List<OrganizationPhone> phones = new ArrayList<>();
}
