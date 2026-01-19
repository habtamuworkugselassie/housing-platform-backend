package com.housingplatform.identity.dto;

import com.housingplatform.identity.domain.Organization;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class OrganizationRequest {
    
    @NotBlank(message = "Organization name is required")
    private String name;
    
    private String registrationNumber;
    
    @NotNull(message = "Organization type is required")
    private Organization.OrganizationType type;
    
    private String address;
    private String city;
    private String country;
    private String phoneNumber;
    
    @jakarta.validation.constraints.Email(message = "Email should be valid")
    private String email;
    
    private String website;
    private String description;
    
    private UUID primaryContactUserId;
}
