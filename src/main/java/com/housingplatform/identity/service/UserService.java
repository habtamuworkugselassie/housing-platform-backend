package com.housingplatform.identity.service;

import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.dto.UserResponse;
import com.housingplatform.identity.dto.UserUpdateRequest;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import com.housingplatform.shared.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    
    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "#id")
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        return userMapper.toResponse(user);
    }
    
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId) {
        return getUserById(userId);
    }
    
    @Transactional(readOnly = true)
    public UserResponse getCurrentUserFromContext() {
        UUID userId = UserContext.getCurrentUserId();
        return getUserById(userId);
    }
    
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(String search, String role, String status, Pageable pageable) {
        Specification<User> spec = Specification.where(null);
        
        // Apply search filter
        if (search != null && !search.trim().isEmpty()) {
            String searchLower = search.trim().toLowerCase();
            Specification<User> searchSpec = (root, query, cb) -> 
                cb.or(
                    cb.like(cb.lower(root.get("email")), "%" + searchLower + "%"),
                    cb.like(cb.lower(root.get("firstName")), "%" + searchLower + "%"),
                    cb.like(cb.lower(root.get("lastName")), "%" + searchLower + "%"),
                    cb.like(cb.lower(root.get("phoneNumber")), "%" + searchLower + "%")
                );
            spec = spec.and(searchSpec);
        }
        
        // Apply role filter
        if (role != null && !role.trim().isEmpty()) {
            try {
                User.UserRole userRole = User.UserRole.valueOf(role.toUpperCase());
                Specification<User> roleSpec = (root, query, cb) -> {
                    query.distinct(true);
                    return cb.isMember(userRole, root.get("roles"));
                };
                spec = spec.and(roleSpec);
            } catch (IllegalArgumentException e) {
                // Invalid role, ignore filter
            }
        }
        
        // Apply status filter
        if (status != null && !status.trim().isEmpty()) {
            try {
                // Map frontend status values to backend enum values
                String statusUpper = status.toUpperCase();
                User.UserStatus userStatus;
                if ("ENABLED".equals(statusUpper) || "ACTIVE".equals(statusUpper)) {
                    userStatus = User.UserStatus.ACTIVE;
                } else if ("DISABLED".equals(statusUpper) || "INACTIVE".equals(statusUpper)) {
                    userStatus = User.UserStatus.INACTIVE;
                } else {
                    userStatus = User.UserStatus.valueOf(statusUpper);
                }
                Specification<User> statusSpec = (root, query, cb) -> 
                    cb.equal(root.get("status"), userStatus);
                spec = spec.and(statusSpec);
            } catch (IllegalArgumentException e) {
                // Invalid status, ignore filter
            }
        }
        
        Page<User> users = userRepository.findAll(spec, pageable);
        return users.map(userMapper::toResponse);
    }
    
    @CacheEvict(value = "users", key = "#id")
    public UserResponse updateUser(UUID id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        
        // Admins can update any user
        // Only admins can update roles and status
        if (UserContext.isAdmin()) {
            if (request.getRoles() != null) {
                user.setRoles(request.getRoles());
            }
            
            if (request.getStatus() != null) {
                user.setStatus(request.getStatus());
            }
            
            // Handle enabled field (map to status for backward compatibility)
            if (request.getEnabled() != null) {
                if (request.getEnabled()) {
                    user.setStatus(User.UserStatus.ACTIVE);
                } else {
                    user.setStatus(User.UserStatus.INACTIVE);
                }
            }
        }
        
        userMapper.updateEntity(user, request);
        User updated = userRepository.save(user);
        return userMapper.toResponse(updated);
    }
}
