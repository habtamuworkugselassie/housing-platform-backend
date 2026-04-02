package com.housingplatform.shared.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller to serve the dynamic XML sitemap for SEO */
@RestController
@Tag(name = "Sitemap", description = "Sitemap generation endpoint")
public class SitemapController {

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

    // Dynamic properties
    // Using ::text cast for UUID in Postgres
    List<String> propertyIds =
        jdbcTemplate.queryForList("SELECT id::varchar FROM properties", String.class);
    for (String id : propertyIds) {
      appendUrl(xml, BASE_URL + "/properties/" + id, "weekly", "0.8");
    }

    // Dynamic buildings
    try {
      List<String> buildingIds =
          jdbcTemplate.queryForList("SELECT id::varchar FROM buildings", String.class);
      for (String id : buildingIds) {
        appendUrl(xml, BASE_URL + "/buildings/" + id, "weekly", "0.8");
      }
    } catch (Exception e) {
    }

    // Dynamic organizations
    try {
      List<String> orgIds =
          jdbcTemplate.queryForList("SELECT id::varchar FROM organizations", String.class);
      for (String id : orgIds) {
        appendUrl(xml, BASE_URL + "/organizations/" + id, "weekly", "0.7");
      }
    } catch (Exception e) {
    }

    xml.append("</urlset>");
    return xml.toString();
  }

  private void appendUrl(StringBuilder xml, String loc, String freq, String priority) {
    xml.append("  <url>\n");
    xml.append("    <loc>").append(loc).append("</loc>\n");
    xml.append("    <changefreq>").append(freq).append("</changefreq>\n");
    xml.append("    <priority>").append(priority).append("</priority>\n");
    xml.append("  </url>\n");
  }
}
