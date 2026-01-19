package com.housingplatform.construction.domain;

import com.housingplatform.shared.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "materials")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Material extends BaseAuditEntity {
    
    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;
    
    @Column(nullable = false)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(nullable = false)
    private String category; // e.g., CEMENT, STEEL, BRICKS, TILES
    
    @Column(nullable = false)
    private String unit; // e.g., KG, TON, BAG, PIECE
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;
    
    @Column(nullable = false)
    private Integer availableQuantity;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaterialStatus status;
    
    private String brand;
    private String specifications; // JSON or text
    
    public enum MaterialStatus {
        AVAILABLE, OUT_OF_STOCK, DISCONTINUED
    }
}
