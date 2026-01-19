package com.housingplatform.banking.service;

import com.housingplatform.banking.domain.FinancingOffer;
import com.housingplatform.banking.dto.FinancingOfferRequest;
import com.housingplatform.banking.dto.FinancingOfferResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface FinancingOfferMapper {
    FinancingOffer toEntity(FinancingOfferRequest request);
    FinancingOfferResponse toResponse(FinancingOffer offer);
    void updateEntity(@MappingTarget FinancingOffer offer, FinancingOfferRequest request);
}
