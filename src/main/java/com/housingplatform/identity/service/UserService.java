package com.housingplatform.identity.service;

import com.housingplatform.identity.dto.UserResponse;
import com.housingplatform.identity.dto.UserUpdateRequest;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import com.housingplatform.shared.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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
        com.housingplatform.identity.domain.User user = userRepository.findById(id)
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
    
    @CacheEvict(value = "users", key = "#id")
    public UserResponse updateUser(UUID id, UserUpdateRequest request) {
        com.housingplatform.identity.domain.User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        
        userMapper.updateEntity(user, request);
        com.housingplatform.identity.domain.User updated = userRepository.save(user);
        return userMapper.toResponse(updated);
    }
}
