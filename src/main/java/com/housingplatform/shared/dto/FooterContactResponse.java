package com.housingplatform.shared.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/** Public footer contact line derived from the configured base organization. */
@Value
@Builder
public class FooterContactResponse {
  String address;

  /**
   * Human-readable primary phone (e.g. +251 913 504 097).
   *
   * @deprecated superseded by {@link #phones}; still populated with the first entry so a frontend
   *     deployed before the list existed keeps working.
   */
  @Deprecated String phoneDisplay;

  /**
   * Digits-only primary phone for a tel: href (e.g. 251913504097).
   *
   * @deprecated superseded by {@link #phones}, as above.
   */
  @Deprecated String phoneTel;

  /** Every contact line the base organization publishes, in its configured display order. */
  List<FooterPhone> phones;

  /** Short website label (e.g. www.dreamteam.com). */
  String websiteLabel;

  /** Full URL with scheme for the link href. */
  String websiteUrl;

  /** One publishable contact line. */
  @Value
  @Builder
  public static class FooterPhone {
    /** Human-readable (e.g. +251 913 504 097). */
    String display;

    /** Digits-only for a tel: href (e.g. 251913504097). */
    String tel;
  }
}
