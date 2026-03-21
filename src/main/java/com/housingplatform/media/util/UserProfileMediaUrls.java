package com.housingplatform.media.util;

import com.housingplatform.media.domain.MediaAttachment;
import java.util.UUID;

/** Resolves public URLs for user profile {@link MediaAttachment} rows (uploads vs legacy BYTEA). */
public final class UserProfileMediaUrls {

  private UserProfileMediaUrls() {}

  /**
   * Prefer {@code image_url} when it is not a mistaken property URL (uploads, users, or absolute
   * URLs); otherwise legacy BYTEA at {@code /api/v1/users/.../profile-image/.../file}.
   */
  public static String profileImageUrl(MediaAttachment att, UUID userId) {
    String raw = att.getImageUrl();
    if (raw != null && !raw.isBlank()) {
      String u = raw.trim();
      // Bad data: user media rows must never serve property image endpoints
      if (!u.contains("/api/v1/properties/")) {
        return u;
      }
    }
    if (att.hasFileData()) {
      return "/api/v1/users/" + userId + "/profile-image/" + att.getId() + "/file";
    }
    return null;
  }
}
