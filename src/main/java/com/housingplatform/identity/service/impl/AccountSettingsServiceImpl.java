package com.housingplatform.identity.service.impl;

import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.dto.ChangePasswordRequest;
import com.housingplatform.identity.dto.PaymentPreferencesRequest;
import com.housingplatform.identity.dto.UserResponse;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.identity.service.AccountSettingsService;
import com.housingplatform.identity.service.UserMapper;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import com.housingplatform.shared.util.PhoneNumberNormalizer;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountSettingsServiceImpl implements AccountSettingsService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final UserMapper userMapper;

  @Override
  public UserResponse changePassword(UUID userId, ChangePasswordRequest request) {
    User user = load(userId);
    if (needsCurrentPassword(user)) {
      String current = request.getCurrentPassword();
      if (current == null
          || current.isEmpty()
          || !passwordEncoder.matches(current, user.getPasswordHash())) {
        throw new BusinessException("Your current password is not correct");
      }
    }
    user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
    user.setPasswordSet(true);
    return userMapper.toResponse(userRepository.save(user));
  }

  /**
   * A known password must be confirmed. Passwordless accounts (quick registration, Google) need no
   * confirmation; nor do older buyer-only accounts where it was never recorded, which were mostly
   * created that way. Staff accounts with an unknown state always confirm, so a stolen session
   * cannot quietly take one over.
   */
  static boolean needsCurrentPassword(User user) {
    if (user.getPasswordSet() != null) {
      return user.getPasswordSet();
    }
    return !Set.of(User.UserRole.BUYER).equals(user.getRoles());
  }

  @Override
  public UserResponse updatePaymentPreferences(UUID userId, PaymentPreferencesRequest request) {
    User user = load(userId);
    String phone = request.getPaymentPhone();
    String normalized = null;
    if (phone != null && !phone.isBlank()) {
      try {
        normalized = PhoneNumberNormalizer.toE164(phone);
      } catch (BusinessException e) {
        throw new BusinessException("Enter the wallet phone number as 09… or +251…");
      }
    }
    user.setPreferredPaymentMethod(request.getPreferredPaymentMethod());
    user.setPaymentPhone(normalized);
    return userMapper.toResponse(userRepository.save(user));
  }

  private User load(UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User", userId));
  }
}
