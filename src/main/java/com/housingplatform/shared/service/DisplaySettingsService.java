package com.housingplatform.shared.service;

import com.housingplatform.shared.dto.DisplaySettingsResponse;
import com.housingplatform.shared.dto.DisplaySettingsUpdateRequest;

public interface DisplaySettingsService {

  String KEY_SPONSOR_CAROUSEL_AUTOPLAY_MS = "SPONSOR_CAROUSEL_AUTOPLAY_MS";
  String KEY_SIDEBAR_MEDIA_ROTATION_MS = "SIDEBAR_MEDIA_ROTATION_MS";
  String KEY_SIDEBAR_LAYOUT_ROTATION_MS = "SIDEBAR_LAYOUT_ROTATION_MS";

  String KEY_EXHIBITION_SPONSORSHIP_PACKAGES_VISIBLE = "EXHIBITION_SPONSORSHIP_PACKAGES_VISIBLE";

  String KEY_EXHIBITION_SPONSORSHIP_PACKAGE_PRICES_VISIBLE =
      "EXHIBITION_SPONSORSHIP_PACKAGE_PRICES_VISIBLE";

  /** Registration number of the organization that supplies the public footer contact block. */
  String KEY_FOOTER_ORGANIZATION_REGISTRATION_NUMBER = "FOOTER_ORGANIZATION_REGISTRATION_NUMBER";

  DisplaySettingsResponse getDisplaySettings();

  DisplaySettingsResponse updateDisplaySettings(DisplaySettingsUpdateRequest request);
}
