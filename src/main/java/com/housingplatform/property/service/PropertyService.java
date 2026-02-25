package com.housingplatform.property.service;

import com.housingplatform.property.dto.PropertyRequest;
import com.housingplatform.property.dto.PropertyResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface PropertyService {
  PropertyResponse createProperty(PropertyRequest request, UUID agentId);

  PropertyResponse getPropertyById(UUID id);

  Page<PropertyResponse> getAllProperties(
      String status, String city, Pageable pageable, boolean publicOnly);

  PropertyResponse updateProperty(UUID id, PropertyRequest request, UUID agentId);

  void deleteProperty(UUID id, UUID agentId);

  List<PropertyResponse> getPropertiesByCompanyId(UUID companyId);

  List<PropertyResponse> getPropertiesByAgentId(UUID agentId);

  List<PropertyResponse> searchProperties(
      String companyName, String city, String state, String country, String title, Integer limit);

  PropertyResponse uploadPropertyMedia(
      UUID id, List<MultipartFile> files, List<String> captions, UUID agentId);

  PropertyResponse deletePropertyImage(UUID id, UUID imageId, UUID agentId);

  org.springframework.http.ResponseEntity<byte[]> getPropertyImageFile(UUID id, UUID imageId);
}
