package com.housingplatform.identity.service;

import com.housingplatform.identity.dto.UserCreateRequest;
import com.housingplatform.identity.dto.UserResponse;
import com.housingplatform.identity.dto.UserUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

  UserResponse getUserById(UUID id);

  UserResponse getCurrentUser(UUID userId);

  UserResponse getCurrentUserFromContext();

  /** Create a new user (admin only). Allows any role including ADMIN. */
  UserResponse createUser(UserCreateRequest request);

  Page<UserResponse> getAllUsers(String search, String role, String status, Pageable pageable);

  UserResponse updateUser(UUID id, UserUpdateRequest request);

  UserResponse uploadProfileImage(UUID userId, MultipartFile file);

  ResponseEntity<byte[]> getUserProfileImage(UUID userId, UUID attachmentId);
}
