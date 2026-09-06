package com.housingplatform.shared.service.impl;

import static com.housingplatform.shared.service.DisplaySettingsService.*;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.dto.OrganizationPhoneDto;
import com.housingplatform.identity.dto.OrganizationResponse;
import com.housingplatform.identity.service.OrganizationService;
import com.housingplatform.shared.domain.SiteSetting;
import com.housingplatform.shared.dto.DisplaySettingsResponse;
import com.housingplatform.shared.dto.DisplaySettingsUpdateRequest;
import com.housingplatform.shared.dto.FooterContactResponse;
import com.housingplatform.shared.repository.SiteSettingRepository;
import com.housingplatform.shared.service.DisplaySettingsService;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DisplaySettingsServiceImpl implements DisplaySettingsService {

  private static final long DEFAULT_SPONSOR_CAROUSEL_MS = 10_000L;
  private static final long DEFAULT_SIDEBAR_MEDIA_MS = 12_000L;
  private static final long DEFAULT_SIDEBAR_LAYOUT_MS = 35_000L;
  private static final boolean DEFAULT_EXHIBITION_SPONSORSHIP_PACKAGES_VISIBLE = true;
  private static final boolean DEFAULT_EXHIBITION_SPONSORSHIP_PACKAGE_PRICES_VISIBLE = true;
  private static final boolean DEFAULT_EXHIBITION_LIVE_VISIBLE = false;
  private static final String DEFAULT_LIVE_SOURCE_TYPE = "EXTERNAL_EMBED";

  /** Default base org seed (migration V34 / scripts/create-dream-teams-organization.js). */
  private static final String DEFAULT_FOOTER_ORG_REGISTRATION = "DTT-PLC-FOOTER-001";

  private final SiteSettingRepository siteSettingRepository;
  private final OrganizationService organizationService;

  @Override
  @Transactional(readOnly = true)
  public DisplaySettingsResponse getDisplaySettings() {
    Map<String, String> map =
        siteSettingRepository.findAll().stream()
            .collect(Collectors.toMap(SiteSetting::getSettingKey, SiteSetting::getSettingValue));

    return DisplaySettingsResponse.builder()
        .sponsorCarouselAutoplayMs(
            parseLong(map, KEY_SPONSOR_CAROUSEL_AUTOPLAY_MS, DEFAULT_SPONSOR_CAROUSEL_MS))
        .sidebarMediaRotationMs(
            parseLong(map, KEY_SIDEBAR_MEDIA_ROTATION_MS, DEFAULT_SIDEBAR_MEDIA_MS))
        .sidebarLayoutRotationMs(
            parseLong(map, KEY_SIDEBAR_LAYOUT_ROTATION_MS, DEFAULT_SIDEBAR_LAYOUT_MS))
        .exhibitionSponsorshipPackagesVisible(
            parseBoolean(
                map,
                KEY_EXHIBITION_SPONSORSHIP_PACKAGES_VISIBLE,
                DEFAULT_EXHIBITION_SPONSORSHIP_PACKAGES_VISIBLE))
        .exhibitionSponsorshipPackagePricesVisible(
            parseBoolean(
                map,
                KEY_EXHIBITION_SPONSORSHIP_PACKAGE_PRICES_VISIBLE,
                DEFAULT_EXHIBITION_SPONSORSHIP_PACKAGE_PRICES_VISIBLE))
        .exhibitionLiveVisible(
            parseBoolean(map, KEY_EXHIBITION_LIVE_VISIBLE, DEFAULT_EXHIBITION_LIVE_VISIBLE))
        .liveSourceType(parseString(map, KEY_LIVE_SOURCE_TYPE, DEFAULT_LIVE_SOURCE_TYPE))
        .liveEmbedUrl(parseString(map, KEY_LIVE_EMBED_URL, ""))
        .liveHlsUrl(parseString(map, KEY_LIVE_HLS_URL, ""))
        .liveTitle(parseString(map, KEY_LIVE_TITLE, ""))
        .liveYoutubeUrl(parseString(map, KEY_LIVE_YOUTUBE_URL, ""))
        .liveTiktokUrl(parseString(map, KEY_LIVE_TIKTOK_URL, ""))
        .liveFacebookUrl(parseString(map, KEY_LIVE_FACEBOOK_URL, ""))
        .exhibitionFeedbackVisible(parseBoolean(map, KEY_EXHIBITION_FEEDBACK_VISIBLE, false))
        .exhibitionFeedbackAutoPublish(parseBoolean(map, KEY_EXHIBITION_FEEDBACK_AUTO_PUBLISH, false))
        .exhibitionLiveAutoSimulcast(
            parseBoolean(map, KEY_EXHIBITION_LIVE_AUTO_SIMULCAST, false))
        .footer(resolveFooterContact(map))
        .build();
  }

  @Override
  @Transactional
  public DisplaySettingsResponse updateDisplaySettings(DisplaySettingsUpdateRequest request) {
    upsert(KEY_SPONSOR_CAROUSEL_AUTOPLAY_MS, String.valueOf(request.sponsorCarouselAutoplayMs()));
    upsert(KEY_SIDEBAR_MEDIA_ROTATION_MS, String.valueOf(request.sidebarMediaRotationMs()));
    upsert(KEY_SIDEBAR_LAYOUT_ROTATION_MS, String.valueOf(request.sidebarLayoutRotationMs()));
    upsert(
        KEY_EXHIBITION_SPONSORSHIP_PACKAGES_VISIBLE,
        Boolean.TRUE.equals(request.exhibitionSponsorshipPackagesVisible()) ? "true" : "false");
    upsert(
        KEY_EXHIBITION_SPONSORSHIP_PACKAGE_PRICES_VISIBLE,
        Boolean.TRUE.equals(request.exhibitionSponsorshipPackagePricesVisible())
            ? "true"
            : "false");
    // Live broadcast fields are optional; only write those actually provided so a
    // partial update never wipes stored values.
    if (request.exhibitionLiveVisible() != null) {
      upsert(
          KEY_EXHIBITION_LIVE_VISIBLE,
          Boolean.TRUE.equals(request.exhibitionLiveVisible()) ? "true" : "false");
    }
    if (request.liveSourceType() != null) {
      upsert(KEY_LIVE_SOURCE_TYPE, normalizeSourceType(request.liveSourceType()));
    }
    if (request.liveEmbedUrl() != null) {
      upsert(KEY_LIVE_EMBED_URL, request.liveEmbedUrl().trim());
    }
    if (request.liveHlsUrl() != null) {
      upsert(KEY_LIVE_HLS_URL, request.liveHlsUrl().trim());
    }
    if (request.liveTitle() != null) {
      upsert(KEY_LIVE_TITLE, request.liveTitle().trim());
    }
    if (request.liveYoutubeUrl() != null) {
      upsert(KEY_LIVE_YOUTUBE_URL, request.liveYoutubeUrl().trim());
    }
    if (request.liveTiktokUrl() != null) {
      upsert(KEY_LIVE_TIKTOK_URL, request.liveTiktokUrl().trim());
    }
    if (request.liveFacebookUrl() != null) {
      upsert(KEY_LIVE_FACEBOOK_URL, request.liveFacebookUrl().trim());
    }
    if (request.exhibitionFeedbackVisible() != null) {
      upsert(
          KEY_EXHIBITION_FEEDBACK_VISIBLE,
          Boolean.TRUE.equals(request.exhibitionFeedbackVisible()) ? "true" : "false");
    }
    if (request.exhibitionFeedbackAutoPublish() != null) {
      upsert(
          KEY_EXHIBITION_FEEDBACK_AUTO_PUBLISH,
          Boolean.TRUE.equals(request.exhibitionFeedbackAutoPublish()) ? "true" : "false");
    }
    if (request.exhibitionLiveAutoSimulcast() != null) {
      upsert(
          KEY_EXHIBITION_LIVE_AUTO_SIMULCAST,
          Boolean.TRUE.equals(request.exhibitionLiveAutoSimulcast()) ? "true" : "false");
    }
    return getDisplaySettings();
  }

  private void upsert(String key, String value) {
    SiteSetting row =
        siteSettingRepository.findById(key).orElse(SiteSetting.builder().settingKey(key).build());
    row.setSettingValue(value);
    siteSettingRepository.save(row);
  }

  private static long parseLong(Map<String, String> map, String key, long defaultMs) {
    String raw = map.get(key);
    if (raw == null || raw.isBlank()) {
      return defaultMs;
    }
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException e) {
      return defaultMs;
    }
  }

  private static boolean parseBoolean(Map<String, String> map, String key, boolean defaultVal) {
    String raw = map.get(key);
    if (raw == null || raw.isBlank()) {
      return defaultVal;
    }
    String v = raw.trim().toLowerCase();
    return "true".equals(v) || "1".equals(v) || "yes".equals(v);
  }

  private static String parseString(Map<String, String> map, String key, String defaultVal) {
    String raw = map.get(key);
    return (raw == null) ? defaultVal : raw;
  }

  private static String normalizeSourceType(String raw) {
    return "HLS".equalsIgnoreCase(raw == null ? "" : raw.trim()) ? "HLS" : "EXTERNAL_EMBED";
  }

  private FooterContactResponse resolveFooterContact(Map<String, String> map) {
    String raw = map.get(KEY_FOOTER_ORGANIZATION_REGISTRATION_NUMBER);
    String reg = (raw == null || raw.isBlank()) ? DEFAULT_FOOTER_ORG_REGISTRATION : raw.trim();
    if (reg.isBlank()) {
      return null;
    }
    return organizationService
        .getOrganizationByRegistrationNumber(reg.trim())
        .filter(o -> o.getStatus() == Organization.OrganizationStatus.APPROVED)
        .map(this::toFooterContact)
        .orElse(null);
  }

  private FooterContactResponse toFooterContact(OrganizationResponse org) {
    String address =
        firstNonBlank(org.getAddress(), buildCityCountryLine(org.getCity(), org.getCountry()));
    List<FooterContactResponse.FooterPhone> phones = toFooterPhones(org.getPhoneNumbers());
    FooterContactResponse.FooterPhone primary = phones.isEmpty() ? null : phones.get(0);
    return FooterContactResponse.builder()
        .address(address)
        .phoneDisplay(primary != null ? primary.getDisplay() : "")
        .phoneTel(primary != null ? primary.getTel() : "")
        .phones(phones)
        .websiteLabel(websiteLabel(org.getWebsite()))
        .websiteUrl(normalizeWebsiteHref(org.getWebsite()))
        .build();
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) {
      return a.trim();
    }
    if (b != null && !b.isBlank()) {
      return b.trim();
    }
    return "";
  }

  private static String buildCityCountryLine(String city, String country) {
    String c = city != null ? city.trim() : "";
    String co = country != null ? country.trim() : "";
    if (c.isEmpty() && co.isEmpty()) {
      return "";
    }
    if (c.isEmpty()) {
      return co;
    }
    if (co.isEmpty()) {
      return c;
    }
    return c + ", " + co;
  }

  /**
   * The organization's phones already arrive in display order (OrganizationContact orders them by
   * displayOrder), so an organization that publishes a second contact line gets it in the footer
   * without any further configuration. This used to take phones.get(0) and drop the rest.
   */
  private static List<FooterContactResponse.FooterPhone> toFooterPhones(
      List<OrganizationPhoneDto> phones) {
    if (phones == null) {
      return List.of();
    }
    return phones.stream()
        .filter(p -> p != null && p.getNumber() != null && !p.getNumber().isBlank())
        .map(
            p ->
                FooterContactResponse.FooterPhone.builder()
                    .display(formatPhoneDisplay(p))
                    .tel(formatPhoneTelDigits(p))
                    .build())
        .collect(Collectors.toList());
  }

  private static String formatPhoneDisplay(OrganizationPhoneDto p) {
    String cc = p.getCountryCode() != null ? p.getCountryCode().trim() : "";
    if (!cc.startsWith("+") && !cc.isEmpty()) {
      cc = "+" + cc.replace("+", "");
    }
    String num = p.getNumber().trim().replaceAll("\\s+", " ");
    return (cc + " " + num).trim();
  }

  private static String formatPhoneTelDigits(OrganizationPhoneDto p) {
    String cc = p.getCountryCode() != null ? p.getCountryCode().replaceAll("[^0-9]", "") : "";
    String num = p.getNumber() != null ? p.getNumber().replaceAll("[^0-9]", "") : "";
    return cc + num;
  }

  private static String normalizeWebsiteHref(String website) {
    if (website == null || website.isBlank()) {
      return "";
    }
    String w = website.trim();
    if (w.startsWith("http://") || w.startsWith("https://")) {
      return w;
    }
    return "https://" + w;
  }

  private static String websiteLabel(String website) {
    if (website == null || website.isBlank()) {
      return "";
    }
    String href = normalizeWebsiteHref(website);
    try {
      URI u = URI.create(href);
      String host = u.getHost();
      if (host != null && !host.isEmpty()) {
        String path = u.getPath();
        if (path != null && path.length() > 1) {
          return host + path;
        }
        return host;
      }
    } catch (Exception ignored) {
    }
    return website.replaceFirst("^https?://", "").replaceAll("/$", "");
  }
}
