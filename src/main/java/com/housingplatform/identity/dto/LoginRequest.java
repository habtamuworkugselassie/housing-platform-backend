package com.housingplatform.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    
    @NotBlank(message = "Username, email, or phone number is required")
    private String username; // Can be email, phone, or username
    
    @NotBlank(message = "Password is required")
    private String password;
}
