package com.housingplatform.outreach.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/** Subject and body for one sector in one language. Placeholders: see TemplateRenderer. */
@Entity
@Table(name = "outreach_template")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class OutreachTemplate extends BaseEntity {

  @Column(name = "template_key", nullable = false, length = 40)
  private String templateKey;

  @Column(nullable = false, length = 8)
  private String locale;

  @Column(nullable = false, length = 300)
  private String subject;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String body;
}
