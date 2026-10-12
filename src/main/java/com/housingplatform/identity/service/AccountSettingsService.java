package com.housingplatform.identity.service;

import com.housingplatform.identity.dto.ChangePasswordRequest;
import com.housingplatform.identity.dto.PaymentPreferencesRequest;
import com.housingplatform.identity.dto.UserResponse;
import java.util.UUID;

/** Settings a signed-in user manages for themselves on their profile. */
public interface AccountSettingsService {

  /** Sets a first password, or changes the current one (which must then be given). */
  UserResponse changePassword(UUID userId, ChangePasswordRequest request);

  UserResponse updatePaymentPreferences(UUID userId, PaymentPreferencesRequest request);
}
