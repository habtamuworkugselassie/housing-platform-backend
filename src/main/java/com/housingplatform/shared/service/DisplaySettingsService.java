package com.housingplatform.shared.service;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.identity.dto.OrganizationPhoneDto;
import com.housingplatform.identity.dto.OrganizationResponse;
import com.housingplatform.identity.service.OrganizationService;
import com.housingplatform.shared.domain.SiteSetting;
import com.housingplatform.shared.dto.DisplaySettingsResponse;
import com.housingplatform.shared.dto.DisplaySettingsUpdateRequest;
import com.housingplatform.shared.dto.FooterContactResponse;
import com.housingplatform.shared.repository.SiteSettingRepository;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DisplaySettingsService {

  public static final String KEY_SPONSOR_CAROUSEL_AUTOPLAY_MS = "SPONSOR_CAROUSEL_AUTOPLAY_MS";
  public static final String KEY_SIDEBAR_MEDIA_ROTATION_MS = "SIDEBAR_MEDIA_ROTATION_MS";
  public static final String KEY_SIDEBAR_LAYOUT_ROTATION_MS = "SIDEBAR_LAYOUT_ROTATION_MS";

  /** Registration number of the organization that supplies the public footer contact block. */
  public static final String KEY_FOOTER_ORGANIZATION_REGISTRATION_NUMBER =
      "FOOTER_ORGANIZATION_REGISTRATION_NUMBER";

  private static final long DEFAULT_SPONSOR_CAROUSEL_MS = 10_000L;
  private static final long DEFAULT_SIDEBAR_MEDIA_MS = 12_000L;
  private static final long DEFAULT_SIDEBAR_LAYOUT_MS = 35_000L;

  /** Default base org seed (migration V34 / scripts/create-dream-teams-organization.js). */
  private static final String DEFAULT_FOOTER_ORG_REGISTRATION = "DTT-PLC-FOOTER-001";

  private final SiteSettingRepository siteSettingRepository;
  private final OrganizationService organizationService;

  @Transactional(readOnly = true)
  public DisplaySettingsResponse getDisplaySettings() {
    Map<String, String> map =
        siteSettingRepository.findAll().stream()
            .collect(Collectors.toMap(SiteSetting::getSettingKey, SiteSetting::getSettingValue));

    return DisplaySettingsResponse.builder()
        .sponsorCarouselAutoplayMs(parseLong(map, KEY_SPONSOR_CAROUSEL_AUTOPLAY_MS, DEFAULT_SPONSOR_CAROUSEL_MS))
        .sidebarMediaRotationMs(parseLong(map, KEY_SIDEBAR_MEDIA_ROTATION_MS, DEFAULT_SIDEBAR_MEDIA_MS))
        .sidebarLayoutRotationMs(parseLong(map, KEY_SIDEBAR_LAYOUT_ROTATION_MS, DEFAULT_SIDEBAR_LAYOUT_MS))
        .footer(resolveFooterContact(map))
        .build();
  }

  @Transactional
  public DisplaySettingsResponse updateDisplaySettings(DisplaySettingsUpdateRequest request) {
    upsert(KEY_SPONSOR_CAROUSEL_AUTOPLAY_MS, String.valueOf(request.sponsorCarouselAutoplayMs()));
    upsert(KEY_SIDEBAR_MEDIA_ROTATION_MS, String.valueOf(request.sidebarMediaRotationMs()));
    upsert(KEY_SIDEBAR_LAYOUT_ROTATION_MS, String.valueOf(request.sidebarLayoutRotationMs()));
    return getDisplaySettings();
  }

  private void upsert(String key, String value) {
    SiteSetting row =
        siteSettingRepository
            .findById(key)
            .orElse(SiteSetting.builder().settingKey(key).build());
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

  private FooterContactResponse resolveFooterContact(Map<String, String> map) {
    String raw = map.get(KEY_FOOTER_ORGANIZATION_REGISTRATION_NUMBER);
    String reg =
        (raw == null || raw.isBlank()) ? DEFAULT_FOOTER_ORG_REGISTRATION : raw.trim();
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
        firstNonBlank(
            org.getAddress(),
            buildCityCountryLine(org.getCity(), org.getCountry()));
    return FooterContactResponse.builder()
        .address(address)
        .phoneDisplay(formatPhoneDisplay(org.getPhoneNumbers()))
        .phoneTel(formatPhoneTelDigits(org.getPhoneNumbers()))
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

  private static String formatPhoneDisplay(List<OrganizationPhoneDto> phones) {
    if (phones == null || phones.isEmpty()) {
      return "";
    }
    OrganizationPhoneDto p = phones.get(0);
    if (p == null || p.getNumber() == null || p.getNumber().isBlank()) {
      return "";
    }
    String cc = p.getCountryCode() != null ? p.getCountryCode().trim() : "";
    if (!cc.startsWith("+") && !cc.isEmpty()) {
      cc = "+" + cc.replace("+", "");
    }
    String num = p.getNumber().trim().replaceAll("\\s+", " ");
    return (cc + " " + num).trim();
  }

  private static String formatPhoneTelDigits(List<OrganizationPhoneDto> phones) {
    if (phones == null || phones.isEmpty()) {
      return "";
    }
    OrganizationPhoneDto p = phones.get(0);
    if (p == null) {
      return "";
    }
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
