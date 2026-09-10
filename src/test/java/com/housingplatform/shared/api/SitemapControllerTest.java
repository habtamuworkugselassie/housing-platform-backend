package com.housingplatform.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The sitemap is the one thing on this site written for a machine that no person reads, so a
 * mistake in it is invisible until it shows up as coverage warnings in Search Console weeks later.
 * These tests pin the properties that actually matter to a crawler: no duplicate URLs, no
 * non-canonical URLs, and no lastmod that was not read from a real row.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SitemapControllerTest {

  private static final LocalDateTime UPDATED = LocalDateTime.of(2026, 3, 14, 9, 30);

  @Mock private JdbcTemplate jdbcTemplate;

  @InjectMocks private SitemapController controller;

  private void stubEmptyDatabase() {
    when(jdbcTemplate.queryForObject(anyString(), eq(LocalDateTime.class), any(Object[].class)))
        .thenReturn(null);
    when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
  }

  private void stubPopulatedDatabase(UUID propertyId) {
    when(jdbcTemplate.queryForObject(anyString(), eq(LocalDateTime.class), any(Object[].class)))
        .thenReturn(UPDATED);
    when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
    when(jdbcTemplate.queryForList(contains("FROM properties"), any(Object[].class)))
        .thenReturn(
            List.of(
                Map.of(
                    "id", propertyId.toString(),
                    "updated_at", Timestamp.valueOf(UPDATED))));
  }

  /** The single {@code <url>} element whose {@code <loc>} is exactly this URL. */
  private static String urlBlock(String xml, String loc) {
    Matcher matcher =
        Pattern.compile("<url>\\s*<loc>" + Pattern.quote(loc) + "</loc>(.*?)</url>", Pattern.DOTALL)
            .matcher(xml);
    assertThat(matcher.find()).as("a <url> block for %s", loc).isTrue();
    return matcher.group(1);
  }

  private static List<String> locations(String xml) {
    Matcher matcher = Pattern.compile("<loc>(.*?)</loc>").matcher(xml);
    return matcher.results().map(result -> result.group(1)).toList();
  }

  @Test
  void everyUrlIsListedExactlyOnce() {
    stubEmptyDatabase();

    List<String> locs = locations(controller.getSitemap());

    assertThat(locs).isNotEmpty();
    assertThat(locs).doesNotHaveDuplicates();
  }

  @Test
  void omitsUrlsThatCanonicaliseToAnotherPage() {
    stubEmptyDatabase();

    List<String> locs = locations(controller.getSitemap());

    // /exhibition renders the same view as /, /marketplace/real-estate the same view as
    // /real-estate, and the static marketplace page is a 301. A sitemap that lists a URL
    // whose canonical is elsewhere asks Google to crawl a page to be told to look at another.
    assertThat(locs)
        .doesNotContain(
            "https://ethiobuildconnect.et/exhibition",
            "https://ethiobuildconnect.et/marketplace/real-estate",
            "https://ethiobuildconnect.et/ethio-real-estate-marketplace.html");
    assertThat(locs)
        .contains(
            "https://ethiobuildconnect.et/",
            "https://ethiobuildconnect.et/real-estate",
            "https://ethiobuildconnect.et/ethiopia-real-estate-market");
  }

  @Test
  void omitsLastmodWhenThereIsNoRowToReadItFrom() {
    stubEmptyDatabase();

    String xml = controller.getSitemap();

    // An empty database means no page has a known modification date. Emitting today's date
    // anyway is the failure mode that gets a sitemap's dates ignored wholesale.
    assertThat(xml).doesNotContain("<lastmod>");
  }

  @Test
  void reportsEachRowsOwnUpdatedAtAsLastmod() {
    UUID propertyId = UUID.randomUUID();
    stubPopulatedDatabase(propertyId);

    String xml = controller.getSitemap();

    assertThat(xml)
        .contains(
            "<loc>https://ethiobuildconnect.et/properties/"
                + propertyId
                + "</loc>\n    <lastmod>2026-03-14</lastmod>");
    // The index pages inherit the high-water mark rather than going undated. Matched on the
    // <url> block rather than on <loc> being immediately followed by <lastmod>, because the
    // hreflang alternates now sit between the two.
    assertThat(urlBlock(xml, "https://ethiobuildconnect.et/"))
        .contains("<lastmod>2026-03-14</lastmod>");
    assertThat(urlBlock(xml, "https://ethiobuildconnect.et/am"))
        .contains("<lastmod>2026-03-14</lastmod>");
  }

  @Test
  void listsBothLanguageEditionsWithReciprocalAlternates() {
    stubEmptyDatabase();

    String xml = controller.getSitemap();
    List<String> locs = locations(xml);

    assertThat(locs).contains("https://ethiobuildconnect.et/am");
    assertThat(locs).contains("https://ethiobuildconnect.et/am/real-estate");
    assertThat(locs).doesNotHaveDuplicates();

    // hreflang is only honoured when every edition names every edition, itself included.
    String reciprocal =
        "<xhtml:link rel=\"alternate\" hreflang=\"en\" href=\"https://ethiobuildconnect.et/real-estate\"/>\n"
            + "    <xhtml:link rel=\"alternate\" hreflang=\"am\" href=\"https://ethiobuildconnect.et/am/real-estate\"/>\n"
            + "    <xhtml:link rel=\"alternate\" hreflang=\"x-default\" href=\"https://ethiobuildconnect.et/real-estate\"/>";
    assertThat(xml)
        .contains("<loc>https://ethiobuildconnect.et/real-estate</loc>\n    " + reciprocal);
    assertThat(xml)
        .contains("<loc>https://ethiobuildconnect.et/am/real-estate</loc>\n    " + reciprocal);
    assertThat(xml).contains("xmlns:xhtml=\"http://www.w3.org/1999/xhtml\"");
  }

  @Test
  void omitsTheAmharicEditionOfAnEnglishOnlyPage() {
    stubEmptyDatabase();

    String xml = controller.getSitemap();

    // The market guide is English by design and its /am route is noindex. Listing it, or
    // giving it an alternate, would claim a translation that does not exist.
    assertThat(locations(xml)).contains("https://ethiobuildconnect.et/ethiopia-real-estate-market");
    assertThat(locations(xml))
        .doesNotContain("https://ethiobuildconnect.et/am/ethiopia-real-estate-market");
    assertThat(xml)
        .doesNotContain("href=\"https://ethiobuildconnect.et/am/ethiopia-real-estate-market\"");
  }

  @Test
  void survivesAFailingSectionQuery() {
    when(jdbcTemplate.queryForObject(anyString(), eq(LocalDateTime.class), any(Object[].class)))
        .thenReturn(UPDATED);
    when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
        .thenThrow(new RuntimeException("column does not exist"));

    String xml = controller.getSitemap();

    // A partial sitemap is more use to a crawler than a 500.
    assertThat(xml).startsWith("<?xml").endsWith("</urlset>");
    assertThat(locations(xml)).contains("https://ethiobuildconnect.et/");
  }
}
