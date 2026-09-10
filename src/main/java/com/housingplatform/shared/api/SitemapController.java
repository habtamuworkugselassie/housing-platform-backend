package com.housingplatform.shared.api;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.property.domain.Property;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller to serve the dynamic XML sitemap for SEO.
 *
 * <p>A sitemap must list only URLs the public, unauthenticated site will actually serve, once each,
 * in their canonical form. Listing anything else spends crawl budget on pages that answer 404,
 * render an error, or duplicate a page already listed, and those URLs accumulate in Search Console
 * as "Crawled - currently not indexed", which is where this was found.
 *
 * <p>The filters below therefore mirror the public visibility rules exactly: {@link
 * com.housingplatform.identity.service.OrganizationPublicVisibility} (APPROVED only), and the
 * AVAILABLE-only rule that PropertyServiceImpl applies to public property search. Statuses are
 * bound from the enums rather than written as literals — an earlier version of this query tested
 * {@code status != 'SUSPENDED'}, a value that does not exist in PropertyStatus or BuildingStatus at
 * all, so it matched every row and the mistake was invisible.
 *
 * <p>Every URL carries a {@code <lastmod>}, which is the one hint in the sitemap format Google
 * still acts on — it uses it to decide what to recrawl. {@code changefreq} and {@code priority} are
 * kept for the crawlers that read them, but Google states plainly that it ignores both, so nothing
 * here relies on them. lastmod is only ever emitted from a real {@code updated_at}: a fabricated or
 * always-today value is worse than none, because a sitemap caught claiming everything changed today
 * gets its dates discounted wholesale.
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

  /**
   * A URL that is not backed by a single row.
   *
   * <p>{@code lastmod} is null for a page whose content is written in the frontend — its date lives
   * in the git history, not the database, and guessing one would be a false signal. For an index
   * page it is the newest {@code updated_at} among the rows the page lists, which is exactly when
   * that page last changed.
   *
   * <p>Deliberately absent from this list: {@code /exhibition}, which renders the same view as
   * {@code /} and now canonicalises to it; {@code /marketplace/real-estate}, which does the same
   * against {@code /real-estate}; and {@code /ethio-real-estate-marketplace.html}, four sentences
   * of duplicate copy that nginx now redirects to {@code /real-estate}. Asking Google to crawl a
   * page in order to be told it is a copy of another page wastes the request twice.
   */
  /**
   * A static URL, and whether it has an Amharic edition.
   *
   * <p>{@code amharic} drives both the {@code /am} entry and the hreflang alternates. It is false
   * for pages whose Amharic route is deliberately noindex — the market guide, which is English by
   * design — because a sitemap entry and an alternate are both claims that a translation is
   * published there.
   */
  private record StaticRoute(
      String path, String changefreq, String priority, String lastmod, boolean amharic) {}

  /** URL prefix Amharic is served under. Mirrors src/i18n/localeRoutes.js in the frontend. */
  private static final String AMHARIC_PREFIX = "/am";

  private static final String NEWEST_PUBLIC_PROPERTY =
      "SELECT max(p.updated_at) FROM properties p"
          + " JOIN organizations o ON o.id = p.real_estate_company_id"
          + " WHERE p.status = ? AND o.status = ?";

  private static final String NEWEST_PUBLIC_BUILDING =
      "SELECT max(b.updated_at) FROM buildings b"
          + " JOIN organizations o ON o.id = b.real_estate_company_id"
          + " WHERE o.status = ?";

  private static final String NEWEST_PUBLIC_ORGANIZATION =
      "SELECT max(updated_at) FROM organizations WHERE status = ?";

  /**
   * The static URLs, given the three high-water marks the index pages share.
   *
   * <p>Passed in rather than queried per route: eleven URLs draw on three {@code max(updated_at)}
   * values between them, so the alternative is eight redundant round trips per sitemap fetch.
   */
  private List<StaticRoute> staticRoutes(
      String newestProperty, String newestBuilding, String newestOrganization) {
    return List.of(
        // The expo landing page. Its copy is frontend-side, but the page also renders the
        // newest public listings, so the listings' own high-water mark is a truthful lastmod.
        new StaticRoute("/", "daily", "1.0", newestProperty, true),
        new StaticRoute("/real-estate", "daily", "0.9", newestProperty, true),
        new StaticRoute("/properties", "daily", "0.9", newestProperty, true),
        new StaticRoute("/buildings", "daily", "0.8", newestBuilding, true),
        new StaticRoute("/marketplace/contractors", "weekly", "0.8", newestOrganization, true),
        new StaticRoute("/marketplace/banks", "weekly", "0.7", newestOrganization, true),
        new StaticRoute("/marketplace/insurance", "weekly", "0.7", newestOrganization, true),
        new StaticRoute(
            "/marketplace/consultants-and-architects", "weekly", "0.7", newestOrganization, true),
        new StaticRoute("/marketplace/suppliers", "weekly", "0.7", newestOrganization, true),
        new StaticRoute("/marketplace/finishing-work", "weekly", "0.7", newestOrganization, true),
        // Long-form editorial; it changes when someone edits the frontend, not when a row moves.
        new StaticRoute("/ethiopia-real-estate-market", "monthly", "0.8", null, false));
  }

  @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
  @Operation(summary = "Get Sitemap", description = "Returns the dynamic XML sitemap")
  public String getSitemap() {
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append(
        "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\""
            + " xmlns:xhtml=\"http://www.w3.org/1999/xhtml\">\n");

    Object[] propertyArgs = {
      Property.PropertyStatus.AVAILABLE.name(), Organization.OrganizationStatus.APPROVED.name()
    };
    Object[] approvedOrgArgs = {Organization.OrganizationStatus.APPROVED.name()};

    List<StaticRoute> routes =
        staticRoutes(
            newestUpdate(NEWEST_PUBLIC_PROPERTY, "properties index", propertyArgs),
            newestUpdate(NEWEST_PUBLIC_BUILDING, "buildings index", approvedOrgArgs),
            newestUpdate(NEWEST_PUBLIC_ORGANIZATION, "organizations index", approvedOrgArgs));
    for (StaticRoute route : routes) {
      String english = BASE_URL + route.path();
      if (!route.amharic()) {
        appendUrl(xml, english, route.changefreq(), route.priority(), route.lastmod(), null);
        continue;
      }
      // hreflang has to be reciprocal: every edition lists every edition, itself included,
      // or Google ignores the set. So both entries carry the same block.
      String amharic = BASE_URL + amharicPath(route.path());
      String alternates = alternateLinks(english, amharic);
      appendUrl(xml, english, route.changefreq(), route.priority(), route.lastmod(), alternates);
      appendUrl(xml, amharic, route.changefreq(), route.priority(), route.lastmod(), alternates);
    }

    // Properties: public search serves AVAILABLE only, and hides those whose owning
    // organization is not APPROVED. real_estate_company_id is NOT NULL with a foreign key,
    // so there is no orphan case to allow for here — the service's `org == null ||` guards a
    // missing entry in a lookup map, not a property without a company.
    appendRows(
        xml,
        "/properties/",
        "weekly",
        "0.8",
        "properties",
        "SELECT p.id::varchar AS id, p.updated_at AS updated_at FROM properties p"
            + " JOIN organizations o ON o.id = p.real_estate_company_id"
            + " WHERE p.status = ? AND o.status = ?",
        propertyArgs);

    // Buildings: every BuildingStatus is legitimately public (a PLANNED development is a
    // real page), so the only gate is the owning organization.
    appendRows(
        xml,
        "/buildings/",
        "weekly",
        "0.8",
        "buildings",
        "SELECT b.id::varchar AS id, b.updated_at AS updated_at FROM buildings b"
            + " JOIN organizations o ON o.id = b.real_estate_company_id"
            + " WHERE o.status = ?",
        approvedOrgArgs);

    // Organizations: APPROVED only. PENDING_APPROVAL, REJECTED and SPONSORSHIP_PENDING are
    // all hidden from the public API, so their URLs must not be advertised.
    appendRows(
        xml,
        "/organizations/",
        "weekly",
        "0.7",
        "organizations",
        "SELECT id::varchar AS id, updated_at AS updated_at FROM organizations WHERE status = ?",
        approvedOrgArgs);

    xml.append("</urlset>");
    return xml.toString();
  }

  /**
   * The newest {@code updated_at} among the rows an index page lists, as {@code YYYY-MM-DD}.
   *
   * <p>Returns null on an empty table or a failure, which omits the tag rather than substituting
   * today's date.
   */
  private String newestUpdate(String sql, String label, Object... args) {
    try {
      LocalDateTime newest = jdbcTemplate.queryForObject(sql, LocalDateTime.class, args);
      return newest == null ? null : newest.toLocalDate().toString();
    } catch (Exception e) {
      log.error("Sitemap: failed to read lastmod for {}; omitting the tag", label, e);
      return null;
    }
  }

  /**
   * Runs one query and appends a URL per row, using each row's own {@code updated_at} as lastmod.
   *
   * <p>A failure here is logged rather than swallowed. An earlier version caught Exception and did
   * nothing, so a broken query silently produced a sitemap missing an entire section with no signal
   * anywhere. The remaining sections are still served — a partial sitemap is more useful to a
   * crawler than a 500.
   */
  private void appendRows(
      StringBuilder xml,
      String pathPrefix,
      String freq,
      String priority,
      String label,
      String sql,
      Object... args) {
    try {
      List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
      for (Map<String, Object> row : rows) {
        appendUrl(
            xml,
            BASE_URL + pathPrefix + row.get("id"),
            freq,
            priority,
            toIsoDate(row.get("updated_at")));
      }
    } catch (Exception e) {
      log.error("Sitemap: failed to list {}; omitting that section", label, e);
    }
  }

  /** Renders a timestamp column as {@code YYYY-MM-DD}, or null for anything unexpected. */
  private String toIsoDate(Object value) {
    if (value instanceof java.sql.Timestamp timestamp) {
      return timestamp.toLocalDateTime().toLocalDate().toString();
    }
    if (value instanceof LocalDateTime dateTime) {
      return dateTime.toLocalDate().toString();
    }
    if (value instanceof LocalDate date) {
      return date.toString();
    }
    return null;
  }

  /** {@code /real-estate} in Amharic is {@code /am/real-estate}; the home page is {@code /am}. */
  private String amharicPath(String path) {
    return "/".equals(path) ? AMHARIC_PREFIX : AMHARIC_PREFIX + path;
  }

  /**
   * The reciprocal hreflang block shared by both editions of a page.
   *
   * <p>{@code x-default} names the English URL, which is what an unprefixed path serves.
   */
  private String alternateLinks(String english, String amharic) {
    return xhtmlLink("en", english) + xhtmlLink("am", amharic) + xhtmlLink("x-default", english);
  }

  private String xhtmlLink(String hreflang, String href) {
    return "    <xhtml:link rel=\"alternate\" hreflang=\""
        + hreflang
        + "\" href=\""
        + href
        + "\"/>\n";
  }

  private void appendUrl(
      StringBuilder xml, String loc, String freq, String priority, String lastmod) {
    appendUrl(xml, loc, freq, priority, lastmod, null);
  }

  private void appendUrl(
      StringBuilder xml,
      String loc,
      String freq,
      String priority,
      String lastmod,
      String alternates) {
    xml.append("  <url>\n");
    xml.append("    <loc>").append(loc).append("</loc>\n");
    if (alternates != null) {
      xml.append(alternates);
    }
    if (lastmod != null) {
      xml.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
    }
    xml.append("    <changefreq>").append(freq).append("</changefreq>\n");
    xml.append("    <priority>").append(priority).append("</priority>\n");
    xml.append("  </url>\n");
  }
}
