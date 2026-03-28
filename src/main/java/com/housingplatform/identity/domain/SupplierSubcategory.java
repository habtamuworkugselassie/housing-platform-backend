package com.housingplatform.identity.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "supplier_subcategories")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SupplierSubcategory extends BaseAuditEntity {

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true, length = 100)
  private String slug;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(nullable = false)
  private boolean active;
}
