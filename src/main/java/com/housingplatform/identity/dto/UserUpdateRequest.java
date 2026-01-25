package com.housingplatform.identity.dto;

import com.housingplatform.identity.domain.User;
import jakarta.validation.constraints.Email;
import lombok.Data;

import java.util.Set;

@Data
public class UserUpdateRequest {
    
    // Optional fields - only update if provided
    private String firstName;
    private String lastName;
    private String phoneNumber;
    
    @Email(message = "Email should be valid")
    private String email;
    
    // Admin-only fields
    private Set<User.UserRole> roles;
    private User.UserStatus status;
    private Boolean enabled; // For backward compatibility with frontend
}
