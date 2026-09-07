package com.housingplatform.shared.api;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.property.domain.Property;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller to serve the dynamic XML sitemap for SEO.
 *
 * <p>A sitemap must list only URLs the public, unauthenticated site will actually serve. Listing
 * anything else spends crawl budget on pages that answer 404 or render an error, and those URLs
 * accumulate in Search Console as "Crawled - currently not indexed", which is where this was found.
 *
 * <p>The filters below therefore mirror the public visibility rules exactly: {@link
 * com.housingplatform.identity.service.OrganizationPublicVisibility} (APPROVED only), and the
 * AVAILABLE-only rule that PropertyServiceImpl applies to public property search. Statuses are
 * bound from the enums rather than written as literals — the previous query tested {@code status !=
 * 'SUSPENDED'}, a value that does not exist in PropertyStatus or BuildingStatus at all, so it
 * matched every row and the mistake was invisible.
 */
@RestController
@Tag(name = "Sitemap", description = "Sitemap generation endpoint")
public class SitemapController {

  private static final Logger log = LoggerFactory.getLogger(SitemapController.class);

  private final JdbcTemplate jdbcTemplate;
  private static final String BASE_URL = "https://ethiobuildconnect.et";

  public SitemapController(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
  @Operation(summary = "Get Sitemap", description = "Returns the dynamic XML sitemap")
  public String getSitemap() {
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

    // Static Routes
    String[] staticRoutes = {
      "/",
      "/real-estate",
      "/properties",
      "/buildings",
      "/marketplace/real-estate",
      "/marketplace/contractors",
      "/marketplace/banks",
      "/marketplace/insurance",
      "/marketplace/consultants-and-architects",
      "/marketplace/suppliers",
      "/marketplace/finishing-work",
      "/exhibition",
      "/ethio-real-estate-marketplace.html"
    };
    for (String route : staticRoutes) {
      appendUrl(xml, BASE_URL + route, "daily", "0.9");
    }

    // Properties: public search serves AVAILABLE only, and hides those whose owning
    // organization is not APPROVED. real_estate_company_id is NOT NULL with a foreign key,
    // so there is no orphan case to allow for here — the service's `org == null ||` guards a
    // missing entry in a lookup map, not a property without a company.
    appendIds(
        xml,
        "/properties/",
        "weekly",
        "0.8",
        "properties",
        "SELECT p.id::varchar FROM properties p"
            + " JOIN organizations o ON o.id = p.real_estate_company_id"
            + " WHERE p.status = ? AND o.status = ?",
        Property.PropertyStatus.AVAILABLE.name(),
        Organization.OrganizationStatus.APPROVED.name());

    // Buildings: every BuildingStatus is legitimately public (a PLANNED development is a
    // real page), so the only gate is the owning organization.
    appendIds(
        xml,
        "/buildings/",
        "weekly",
        "0.8",
        "buildings",
        "SELECT b.id::varchar FROM buildings b"
            + " JOIN organizations o ON o.id = b.real_estate_company_id"
            + " WHERE o.status = ?",
        Organization.OrganizationStatus.APPROVED.name());

    // Organizations: APPROVED only. PENDING_APPROVAL, REJECTED and SPONSORSHIP_PENDING are
    // all hidden from the public API, so their URLs must not be advertised.
    appendIds(
        xml,
        "/organizations/",
        "weekly",
        "0.7",
        "organizations",
        "SELECT id::varchar FROM organizations WHERE status = ?",
        Organization.OrganizationStatus.APPROVED.name());

    xml.append("</urlset>");
    return xml.toString();
  }

  /**
   * Runs one id query and appends a URL per row.
   *
   * <p>A failure here is logged rather than swallowed. The previous code caught Exception and did
   * nothing, so a broken query silently produced a sitemap missing an entire section with no signal
   * anywhere. The remaining sections are still served — a partial sitemap is more useful to a
   * crawler than a 500.
   */
  private void appendIds(
      StringBuilder xml,
      String pathPrefix,
      String freq,
      String priority,
      String label,
      String sql,
      Object... args) {
    try {
      List<String> ids = jdbcTemplate.queryForList(sql, String.class, args);
      for (String id : ids) {
        appendUrl(xml, BASE_URL + pathPrefix + id, freq, priority);
      }
    } catch (Exception e) {
      log.error("Sitemap: failed to list {}; omitting that section", label, e);
    }
  }

  private void appendUrl(StringBuilder xml, String loc, String freq, String priority) {
    xml.append("  <url>\n");
    xml.append("    <loc>").append(loc).append("</loc>\n");
    xml.append("    <changefreq>").append(freq).append("</changefreq>\n");
    xml.append("    <priority>").append(priority).append("</priority>\n");
    xml.append("  </url>\n");
  }
}
