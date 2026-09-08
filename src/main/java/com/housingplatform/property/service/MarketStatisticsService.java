package com.housingplatform.property.service;

import com.housingplatform.identity.domain.Organization;
import com.housingplatform.property.domain.Property;
import com.housingplatform.property.dto.MarketStatisticsResponse;
import com.housingplatform.property.dto.MarketStatisticsResponse.Breakdown;
import com.housingplatform.property.dto.MarketStatisticsResponse.CategoryStatistics;
import com.housingplatform.property.dto.MarketStatisticsResponse.MixEntry;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Market statistics computed from the platform's own live listings.
 *
 * <p>Written for a public page that has to compete with sites publishing figures. The advantage
 * here is not analysis, it is provenance: these numbers come from listings this platform holds, so
 * they are current, checkable against the listings themselves, and impossible for an outside
 * aggregator to reproduce.
 *
 * <p>That advantage only survives if the numbers are honest, so the rules below are deliberate and
 * should not be relaxed for a fuller-looking page:
 *
 * <ul>
 *   <li><b>Same visibility as the public site.</b> AVAILABLE listings belonging to APPROVED
 *       organizations — the rule {@code PropertyServiceImpl} applies to public search and {@code
 *       SitemapController} to the sitemap. Statistics that counted withdrawn listings or unapproved
 *       companies would describe a market no visitor can see.
 *   <li><b>Sale and rental are never pooled.</b> A median across a 24,000,000 ETB sale and a 30,000
 *       ETB monthly rent is a meaningless number that looks like a real one.
 *   <li><b>Medians, not means.</b> Property prices are right-skewed; a few high-end villas drag a
 *       mean to where no listing actually sits.
 *   <li><b>Thin buckets report nothing.</b> Below {@link #MINIMUM_SAMPLE_SIZE} priced listings the
 *       price comes back null and the page says so instead of printing a number.
 *   <li><b>Zero and missing values are excluded, not treated as zero.</b> A listing with no price
 *       is unpriced, and counting it as free would understate every median it touches.
 * </ul>
 */
@Service
public class MarketStatisticsService {

  private static final Logger log = LoggerFactory.getLogger(MarketStatisticsService.class);

  /**
   * Fewest priced listings a bucket needs before its median is published.
   *
   * <p>Five is a judgement, not a theorem: low enough that a young marketplace still has something
   * to show, high enough that one unusual asking price cannot become "the median price in Bole".
   */
  static final int MINIMUM_SAMPLE_SIZE = 5;

  /**
   * How long a computed snapshot is served before recomputing.
   *
   * <p>The endpoint is public and unauthenticated, and the query set is six grouped percentile
   * scans. The cache is there so a crawler or a hot link cannot turn a marketing page into a
   * database load generator. Fifteen minutes is far fresher than the daily-or-worse cadence the
   * page's own wording promises.
   */
  private static final Duration CACHE_TTL = Duration.ofMinutes(15);

  /** Common table expression: exactly the listings the public site will show. */
  private static final String VISIBLE_LISTINGS =
      "WITH visible AS ("
          + " SELECT p.category, p.type, p.construction_status, p.price_etb, p.area, p.city"
          + " FROM properties p"
          + " JOIN organizations o ON o.id = p.real_estate_company_id"
          + " WHERE p.status = ? AND o.status = ?"
          + ") ";

  private final JdbcTemplate jdbcTemplate;
  private final AtomicReference<Snapshot> cached = new AtomicReference<>();

  public MarketStatisticsService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  private record Snapshot(Instant computedAt, MarketStatisticsResponse payload) {}

  public MarketStatisticsResponse getMarketStatistics() {
    Snapshot snapshot = cached.get();
    if (snapshot != null
        && Duration.between(snapshot.computedAt(), Instant.now()).compareTo(CACHE_TTL) < 0) {
      return snapshot.payload();
    }
    MarketStatisticsResponse fresh = compute();
    cached.set(new Snapshot(Instant.now(), fresh));
    return fresh;
  }

  private Object[] visibilityArgs() {
    return new Object[] {
      Property.PropertyStatus.AVAILABLE.name(), Organization.OrganizationStatus.APPROVED.name()
    };
  }

  private MarketStatisticsResponse compute() {
    Map<String, CategoryRow> headline = queryHeadline();
    Map<String, List<Breakdown>> byType = queryBreakdown("type", "type::text", 200);
    // City is free text on the listing, so rows are grouped case- and whitespace-insensitively
    // and the commonest spelling in each group becomes the label — otherwise "Addis Ababa",
    // "addis ababa" and " Addis Ababa " are three cities. There is no sub-city column on
    // properties at all, so this is city granularity only; see docs/SEO-TARGETS.md, which
    // records adding one as the change that would unlock the by-neighbourhood table.
    Map<String, List<Breakdown>> byCity =
        queryBreakdown("lower(btrim(city))", "mode() WITHIN GROUP (ORDER BY btrim(city))", 60);

    List<CategoryStatistics> categories = new ArrayList<>();
    for (Map.Entry<String, CategoryRow> entry : headline.entrySet()) {
      String category = entry.getKey();
      CategoryRow row = entry.getValue();
      categories.add(
          new CategoryStatistics(
              category,
              row.listingCount(),
              row.pricedCount(),
              publishable(row.median(), row.pricedCount()),
              publishable(row.lowerQuartile(), row.pricedCount()),
              publishable(row.upperQuartile(), row.pricedCount()),
              row.measuredCount(),
              publishable(row.medianPerSqm(), row.measuredCount()),
              byType.getOrDefault(category, List.of()),
              byCity.getOrDefault(category, List.of())));
    }

    int totalListings = headline.values().stream().mapToInt(CategoryRow::listingCount).sum();

    return new MarketStatisticsResponse(
        LocalDate.now(),
        "ETB",
        MINIMUM_SAMPLE_SIZE,
        totalListings,
        categories,
        queryMix("type", totalListings),
        queryMix("construction_status", totalListings));
  }

  /** Null unless the bucket cleared the sample floor; whole birr otherwise. */
  private Long publishable(Double value, int sampleSize) {
    if (value == null || sampleSize < MINIMUM_SAMPLE_SIZE) return null;
    return Math.round(value);
  }

  private record CategoryRow(
      int listingCount,
      int pricedCount,
      Double median,
      Double lowerQuartile,
      Double upperQuartile,
      int measuredCount,
      Double medianPerSqm) {}

  private Map<String, CategoryRow> queryHeadline() {
    String sql =
        VISIBLE_LISTINGS
            + "SELECT category,"
            + " count(*) AS listing_count,"
            + " count(*) FILTER (WHERE price_etb > 0) AS priced_count,"
            + " percentile_cont(0.5) WITHIN GROUP (ORDER BY price_etb::double precision)"
            + "   FILTER (WHERE price_etb > 0) AS median_price,"
            + " percentile_cont(0.25) WITHIN GROUP (ORDER BY price_etb::double precision)"
            + "   FILTER (WHERE price_etb > 0) AS lower_quartile,"
            + " percentile_cont(0.75) WITHIN GROUP (ORDER BY price_etb::double precision)"
            + "   FILTER (WHERE price_etb > 0) AS upper_quartile,"
            + " count(*) FILTER (WHERE price_etb > 0 AND area > 0) AS measured_count,"
            + " percentile_cont(0.5) WITHIN GROUP (ORDER BY (price_etb / area)::double precision)"
            + "   FILTER (WHERE price_etb > 0 AND area > 0) AS median_per_sqm"
            + " FROM visible GROUP BY category";

    Map<String, CategoryRow> out = new LinkedHashMap<>();
    try {
      for (Map<String, Object> row : jdbcTemplate.queryForList(sql, visibilityArgs())) {
        out.put(
            String.valueOf(row.get("category")),
            new CategoryRow(
                intOf(row.get("listing_count")),
                intOf(row.get("priced_count")),
                doubleOf(row.get("median_price")),
                doubleOf(row.get("lower_quartile")),
                doubleOf(row.get("upper_quartile")),
                intOf(row.get("measured_count")),
                doubleOf(row.get("median_per_sqm"))));
      }
    } catch (Exception e) {
      log.error("Market statistics: headline query failed; serving an empty set", e);
    }
    return out;
  }

  /**
   * One breakdown table per category, keyed by category.
   *
   * <p>{@code groupExpression} and {@code labelExpression} are SQL fragments fixed by the two call
   * sites above. Nothing from a request reaches them — this endpoint takes no parameters at all —
   * and they must stay that way: they are concatenated into the statement, so accepting caller
   * input here would be an injection point.
   */
  private Map<String, List<Breakdown>> queryBreakdown(
      String groupExpression, String labelExpression, int labelLimit) {
    String sql =
        VISIBLE_LISTINGS
            + "SELECT category,"
            + " "
            + labelExpression
            + " AS label,"
            + " count(*) AS listing_count,"
            + " count(*) FILTER (WHERE price_etb > 0) AS priced_count,"
            + " percentile_cont(0.5) WITHIN GROUP (ORDER BY price_etb::double precision)"
            + "   FILTER (WHERE price_etb > 0) AS median_price,"
            + " count(*) FILTER (WHERE price_etb > 0 AND area > 0) AS measured_count,"
            + " percentile_cont(0.5) WITHIN GROUP (ORDER BY (price_etb / area)::double precision)"
            + "   FILTER (WHERE price_etb > 0 AND area > 0) AS median_per_sqm"
            + " FROM visible"
            + " WHERE "
            + groupExpression
            + " IS NOT NULL"
            + " GROUP BY category, "
            + groupExpression
            + " ORDER BY listing_count DESC";

    Map<String, List<Breakdown>> out = new LinkedHashMap<>();
    try {
      for (Map<String, Object> row : jdbcTemplate.queryForList(sql, visibilityArgs())) {
        String label = row.get("label") == null ? "" : String.valueOf(row.get("label")).trim();
        if (label.isEmpty()) continue;
        if (label.length() > labelLimit) label = label.substring(0, labelLimit);
        int pricedCount = intOf(row.get("priced_count"));
        int measuredCount = intOf(row.get("measured_count"));
        out.computeIfAbsent(String.valueOf(row.get("category")), key -> new ArrayList<>())
            .add(
                new Breakdown(
                    label,
                    intOf(row.get("listing_count")),
                    publishable(doubleOf(row.get("median_price")), pricedCount),
                    measuredCount,
                    publishable(doubleOf(row.get("median_per_sqm")), measuredCount)));
      }
    } catch (Exception e) {
      log.error("Market statistics: breakdown by {} failed; omitting it", groupExpression, e);
    }
    return out;
  }

  private List<MixEntry> queryMix(String column, int totalListings) {
    String sql =
        VISIBLE_LISTINGS
            + "SELECT "
            + column
            + " AS bucket, count(*) AS listing_count FROM visible"
            + " WHERE "
            + column
            + " IS NOT NULL GROUP BY "
            + column
            + " ORDER BY listing_count DESC";

    List<MixEntry> out = new ArrayList<>();
    try {
      for (Map<String, Object> row : jdbcTemplate.queryForList(sql, visibilityArgs())) {
        int count = intOf(row.get("listing_count"));
        out.add(
            new MixEntry(
                String.valueOf(row.get("bucket")),
                count,
                totalListings == 0 ? 0d : (double) count / totalListings));
      }
    } catch (Exception e) {
      log.error("Market statistics: {} mix failed; omitting it", column, e);
    }
    return out;
  }

  private static int intOf(Object value) {
    return value instanceof Number number ? number.intValue() : 0;
  }

  private static Double doubleOf(Object value) {
    return value instanceof Number number ? number.doubleValue() : null;
  }
}
