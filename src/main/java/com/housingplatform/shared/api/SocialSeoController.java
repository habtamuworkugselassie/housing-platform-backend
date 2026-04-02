package com.housingplatform.shared.api;

import com.housingplatform.identity.dto.OrganizationResponse;
import com.housingplatform.identity.service.OrganizationService;
import com.housingplatform.property.dto.BuildingResponse;
import com.housingplatform.property.dto.PropertyResponse;
import com.housingplatform.property.service.BuildingService;
import com.housingplatform.property.service.PropertyService;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller to serve dynamic Open Graph HTML exclusively for Social Media bots */
@RestController
@RequestMapping("/seo-proxy")
public class SocialSeoController {

  private final PropertyService propertyService;
  private final BuildingService buildingService;
  private final OrganizationService organizationService;

  private static final String BASE_URL = "https://ethiobuildconnect.et";
  private static final String DEFAULT_IMG = BASE_URL + "/ethio-build-connect-mark.png";

  public SocialSeoController(
      PropertyService propertyService,
      BuildingService buildingService,
      OrganizationService organizationService) {
    this.propertyService = propertyService;
    this.buildingService = buildingService;
    this.organizationService = organizationService;
  }

  @GetMapping(value = "/properties/{id}", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> getPropertySeo(@PathVariable UUID id) {
    try {
      PropertyResponse p = propertyService.getPropertyById(id);
      String title = p.getTitle() != null ? p.getTitle() : "Ethio Build Connect Property";
      String titleFull = title + " | Ethio Build Connect";
      String desc =
          p.getDescription() != null ? p.getDescription() : "View details on Ethio Build Connect.";

      String imageUrl = DEFAULT_IMG;
      if (p.getImages() != null && !p.getImages().isEmpty()) {
        if (p.getImages().get(0) != null && p.getImages().get(0).getImageUrl() != null) {
          imageUrl = p.getImages().get(0).getImageUrl();
        }
      }
      if (!imageUrl.startsWith("http")) {
        imageUrl = BASE_URL + imageUrl;
      }

      return ResponseEntity.ok(
          buildHtml(titleFull, desc, imageUrl, BASE_URL + "/properties/" + id));
    } catch (Exception e) {
      return fallbackHtml();
    }
  }

  @GetMapping(value = "/buildings/{id}", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> getBuildingSeo(@PathVariable UUID id) {
    try {
      BuildingResponse b = buildingService.getBuildingById(id);
      String title = b.getName() != null ? b.getName() : "Building details";
      String desc =
          b.getDescription() != null
              ? b.getDescription()
              : "Building details on Ethio Build Connect.";

      return ResponseEntity.ok(
          buildHtml(
              title + " | Ethio Build Connect", desc, DEFAULT_IMG, BASE_URL + "/buildings/" + id));
    } catch (Exception e) {
      return fallbackHtml();
    }
  }

  @GetMapping(value = "/organizations/{id}", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> getOrganizationSeo(@PathVariable UUID id) {
    try {
      OrganizationResponse org = organizationService.getOrganizationById(id);
      String title = org.getName() != null ? org.getName() : "Organization";
      String desc =
          org.getDescription() != null
              ? org.getDescription()
              : "Organization details on Ethio Build Connect.";

      String logoUrl = org.getLogoUrl() != null ? org.getLogoUrl() : DEFAULT_IMG;
      if (!logoUrl.startsWith("http")) {
        logoUrl = BASE_URL + logoUrl;
      }

      return ResponseEntity.ok(
          buildHtml(
              title + " | Ethio Build Connect", desc, logoUrl, BASE_URL + "/organizations/" + id));
    } catch (Exception e) {
      return fallbackHtml();
    }
  }

  private ResponseEntity<String> fallbackHtml() {
    return ResponseEntity.ok(
        buildHtml("Ethio Build Connect", "Find Real Estate in Ethiopia", DEFAULT_IMG, BASE_URL));
  }

  private String buildHtml(String title, String description, String imageUrl, String url) {
    // We output minimal HTML with OG tags for bots
    // Max description length to avoid parsing issues
    if (description != null && description.length() > 200) {
      description = description.substring(0, 197) + "...";
    }

    return "<!DOCTYPE html>\n"
        + "<html>\n"
        + "<head>\n"
        + "    <meta charset=\"utf-8\">\n"
        + "    <title>"
        + escape(title)
        + "</title>\n"
        + "    <meta name=\"description\" content=\""
        + escape(description)
        + "\">\n"
        + "    <meta property=\"og:type\" content=\"website\">\n"
        + "    <meta property=\"og:title\" content=\""
        + escape(title)
        + "\">\n"
        + "    <meta property=\"og:description\" content=\""
        + escape(description)
        + "\">\n"
        + "    <meta property=\"og:image\" content=\""
        + escape(imageUrl)
        + "\">\n"
        + "    <meta property=\"og:url\" content=\""
        + escape(url)
        + "\">\n"
        + "    <meta name=\"twitter:card\" content=\"summary_large_image\">\n"
        + "</head>\n"
        + "<body>\n"
        + "    <h1>"
        + escape(title)
        + "</h1>\n"
        + "    <p>"
        + escape(description)
        + "</p>\n"
        + "    <img src=\""
        + escape(imageUrl)
        + "\" alt=\"Preview Image\">\n"
        + "</body>\n"
        + "</html>";
  }

  private String escape(String text) {
    if (text == null) return "";
    return text.replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
