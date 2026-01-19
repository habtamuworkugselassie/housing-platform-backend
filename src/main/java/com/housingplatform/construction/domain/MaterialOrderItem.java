package com.housingplatform.construction.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "material_order_items")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class MaterialOrderItem extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private MaterialOrder order;
    
    @Column(name = "material_id")
    private UUID materialId; // Reference to material catalog
    
    @Column(nullable = false)
    private String materialName;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(nullable = false)
    private String category;
    
    @Column(nullable = false)
    private String unit; // e.g., KG, TON, BAG, PIECE
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;
    
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;
    
    @Column(name = "total_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalPrice;
    
    @Column(name = "received_quantity", precision = 10, scale = 2)
    private BigDecimal receivedQuantity; // For partial deliveries
    
    @Column(name = "brand")
    private String brand;
    
    @Column(columnDefinition = "TEXT")
    private String specifications;
    
    private Integer sequence;
}
