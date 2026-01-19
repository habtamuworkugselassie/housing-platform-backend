package com.housingplatform.property.domain;

import com.housingplatform.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "property_images")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PropertyImage extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;
    
    @Column(nullable = false)
    private String imageUrl;
    
    private String caption;
    
    @Column(nullable = false)
    private Integer displayOrder;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean isPrimary = false;
}
