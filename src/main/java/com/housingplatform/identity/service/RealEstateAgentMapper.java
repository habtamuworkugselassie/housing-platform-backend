package com.housingplatform.identity.service;

import com.housingplatform.identity.domain.RealEstateAgent;
import com.housingplatform.identity.dto.AgentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface RealEstateAgentMapper {
    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "organization.id", target = "organizationId")
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "organization", ignore = true)
    AgentResponse toResponse(RealEstateAgent agent);
}
