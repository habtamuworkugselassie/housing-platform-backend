package com.housingplatform.purchase.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "purchase_order_status_history")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PurchaseOrderStatusHistory extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "purchase_order_id", nullable = false)
  private PropertyPurchaseOrder purchaseOrder;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_status", length = 32)
  private PropertyPurchaseOrder.PurchaseOrderStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", nullable = false, length = 32)
  private PropertyPurchaseOrder.PurchaseOrderStatus toStatus;

  @Column(name = "changed_by")
  private String changedBy;

  @Column(name = "changed_at", nullable = false)
  private LocalDateTime changedAt;

  @Column(columnDefinition = "TEXT")
  private String notes;
}
