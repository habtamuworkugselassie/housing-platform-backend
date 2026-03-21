package com.housingplatform.shared.dto;

import lombok.Builder;
import lombok.Value;

/** Public footer contact line derived from the configured base organization. */
@Value
@Builder
public class FooterContactResponse {
  String address;
  /** Human-readable phone (e.g. +251 913 504 097). */
  String phoneDisplay;
  /** Digits-only for tel: href (e.g. 251913504097). */
  String phoneTel;
  /** Short website label (e.g. www.dreamteam.com). */
  String websiteLabel;
  /** Full URL with scheme for the link href. */
  String websiteUrl;
}
