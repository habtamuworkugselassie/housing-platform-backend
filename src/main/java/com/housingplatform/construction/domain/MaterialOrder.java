package com.housingplatform.construction.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "material_orders")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class MaterialOrder extends BaseAuditEntity {
    
    @Column(nullable = false, unique = true)
    private String orderNumber; // e.g., MO-2024-001
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private ConstructionProject project;
    
    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId; // Organization ID with type SUPPLIER
    
    @Column(name = "ordered_by", nullable = false)
    private UUID orderedBy; // User ID who placed the order
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;
    
    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;
    
    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;
    
    @Column(name = "actual_delivery_date")
    private LocalDate actualDeliveryDate;
    
    @Column(name = "subtotal", precision = 19, scale = 2)
    private BigDecimal subtotal;
    
    @Column(name = "tax_amount", precision = 19, scale = 2)
    private BigDecimal taxAmount;
    
    @Column(name = "shipping_cost", precision = 19, scale = 2)
    private BigDecimal shippingCost;
    
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;
    
    @Column(name = "currency", nullable = false)
    @Enumerated(EnumType.STRING)
    private com.housingplatform.shared.domain.Currency currency;
    
    @Column(columnDefinition = "TEXT")
    private String notes;
    
    @Column(name = "delivery_address")
    private String deliveryAddress;
    
    @Column(name = "delivery_city")
    private String deliveryCity;
    
    @Column(name = "delivery_state")
    private String deliveryState;
    
    @Column(name = "delivery_country")
    private String deliveryCountry;
    
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MaterialOrderItem> items = new ArrayList<>();
    
    public enum OrderStatus {
        DRAFT, PENDING, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, CANCELLED, RETURNED
    }
}
