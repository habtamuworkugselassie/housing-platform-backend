package com.housingplatform.property.service;

import com.housingplatform.property.domain.Property;
import com.housingplatform.property.dto.PropertyRequest;
import com.housingplatform.property.dto.PropertyResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PropertyService {
    PropertyResponse createProperty(PropertyRequest request, UUID agentId);
    PropertyResponse getPropertyById(UUID id);
    Page<PropertyResponse> getAllProperties(String status, String city, Pageable pageable, boolean publicOnly);
    PropertyResponse updateProperty(UUID id, PropertyRequest request, UUID agentId);
    void deleteProperty(UUID id, UUID agentId);
    List<PropertyResponse> getPropertiesByCompanyId(UUID companyId);
    List<PropertyResponse> getPropertiesByAgentId(UUID agentId);
    List<PropertyResponse> searchProperties(String companyName, String city, String state, String country, String title, Integer limit);
}
