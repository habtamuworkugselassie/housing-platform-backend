package com.housingplatform.purchase.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.*;
import lombok.experimental.SuperBuilder;

/** One line of the balance schedule the seller sets, e.g. "Second instalment, 40 %, 30 Nov". */
@Entity
@Table(name = "purchase_balance_instalments")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PurchaseBalanceInstalment extends BaseAuditEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "purchase_order_id", nullable = false)
  private PropertyPurchaseOrder purchaseOrder;

  @Column(nullable = false)
  private Integer sequence;

  @Column(nullable = false, length = 100)
  private String label;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(name = "due_date")
  private LocalDate dueDate;
}
