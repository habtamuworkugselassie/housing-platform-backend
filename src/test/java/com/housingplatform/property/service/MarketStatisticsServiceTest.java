package com.housingplatform.property.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;

import com.housingplatform.property.dto.MarketStatisticsResponse;
import com.housingplatform.property.dto.MarketStatisticsResponse.Breakdown;
import com.housingplatform.property.dto.MarketStatisticsResponse.CategoryStatistics;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The published numbers are a credibility bet: the page's whole argument is that figures from our
 * own listings beat an outside aggregator's estimates. A median quietly computed over two asking
 * prices, or one that pooled a monthly rent with a villa sale, would lose that bet in public.
 *
 * <p>The row shapes stubbed here were taken from the real query output — the statements this
 * service builds were run against PostgreSQL 16 with a fixture covering sold listings, unapproved
 * organizations, unpriced rows, missing floor areas and three spellings of "Addis Ababa". These
 * tests cover what happens to those rows after SQL hands them back.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketStatisticsServiceTest {

  @Mock private JdbcTemplate jdbcTemplate;

  private static Map<String, Object> row(Object... keyValues) {
    Map<String, Object> row = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      row.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
    }
    return row;
  }

  /** Mirrors the observed output: 8 priced apartments for sale, 6 rentals, 3 villas. */
  private void stubTypicalDatabase() {
    when(jdbcTemplate.queryForList(contains("lower_quartile"), any(Object[].class)))
        .thenReturn(
            List.of(
                row(
                    "category",
                    "FOR_SALE",
                    "listing_count",
                    14,
                    "priced_count",
                    12,
                    "median_price",
                    21000000.0,
                    "lower_quartile",
                    15500000.0,
                    "upper_quartile",
                    42250000.0,
                    "measured_count",
                    11,
                    "median_per_sqm",
                    137500.0),
                row(
                    "category",
                    "FOR_RENTAL",
                    "listing_count",
                    6,
                    "priced_count",
                    6,
                    "median_price",
                    42500.0,
                    "lower_quartile",
                    36250.0,
                    "upper_quartile",
                    48750.0,
                    "measured_count",
                    6,
                    "median_per_sqm",
                    472.22222222222223)));

    when(jdbcTemplate.queryForList(contains("type::text"), any(Object[].class)))
        .thenReturn(
            List.of(
                row(
                    "category",
                    "FOR_SALE",
                    "label",
                    "APARTMENT",
                    "listing_count",
                    8,
                    "priced_count",
                    8,
                    "median_price",
                    19000000.0,
                    "measured_count",
                    8,
                    "median_per_sqm",
                    130952.38095238095),
                // Three priced villas — under the floor, so no price may be published.
                row(
                    "category",
                    "FOR_SALE",
                    "label",
                    "VILLA",
                    "listing_count",
                    3,
                    "priced_count",
                    3,
                    "median_price",
                    92000000.0,
                    "measured_count",
                    3,
                    "median_per_sqm",
                    230000.0),
                // Priced once, measured never: the two counts must not be conflated.
                row(
                    "category",
                    "FOR_SALE",
                    "label",
                    "HOUSE",
                    "listing_count",
                    3,
                    "priced_count",
                    1,
                    "median_price",
                    5000000.0,
                    "measured_count",
                    0,
                    "median_per_sqm",
                    null)));

    when(jdbcTemplate.queryForList(contains("mode()"), any(Object[].class)))
        .thenReturn(
            List.of(
                row(
                    "category",
                    "FOR_SALE",
                    "label",
                    "Addis Ababa",
                    "listing_count",
                    11,
                    "priced_count",
                    9,
                    "median_price",
                    18000000.0,
                    "measured_count",
                    8,
                    "median_per_sqm",
                    130952.38095238095)));

    when(jdbcTemplate.queryForList(contains("type AS bucket"), any(Object[].class)))
        .thenReturn(
            List.of(
                row("bucket", "APARTMENT", "listing_count", 14),
                row("bucket", "HOUSE", "listing_count", 3),
                row("bucket", "VILLA", "listing_count", 3)));

    when(jdbcTemplate.queryForList(contains("construction_status AS bucket"), any(Object[].class)))
        .thenReturn(List.of(row("bucket", "READY_TO_MOVE", "listing_count", 14)));
  }

  private CategoryStatistics category(MarketStatisticsResponse response, String name) {
    return response.byCategory().stream()
        .filter(c -> c.category().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private Breakdown byType(CategoryStatistics category, String label) {
    return category.byType().stream()
        .filter(b -> b.label().equals(label))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void keepsSaleAndRentalPricesApart() {
    stubTypicalDatabase();

    MarketStatisticsResponse response =
        new MarketStatisticsService(jdbcTemplate).getMarketStatistics();

    // Pooling these would produce a median that is arithmetically real and completely meaningless.
    assertThat(category(response, "FOR_SALE").medianPrice()).isEqualTo(21_000_000L);
    assertThat(category(response, "FOR_RENTAL").medianPrice()).isEqualTo(42_500L);
    assertThat(response.totalListings()).isEqualTo(20);
  }

  @Test
  void refusesToPublishAMedianOverTooFewListings() {
    stubTypicalDatabase();

    CategoryStatistics forSale =
        category(new MarketStatisticsService(jdbcTemplate).getMarketStatistics(), "FOR_SALE");

    // Three villas and one priced house are below the floor: no price, but the listing counts
    // are still reported, so the page can say how many there are without pricing them.
    Breakdown villas = byType(forSale, "VILLA");
    assertThat(villas.medianPrice()).isNull();
    assertThat(villas.medianPricePerSqm()).isNull();
    assertThat(villas.listingCount()).isEqualTo(3);

    Breakdown houses = byType(forSale, "HOUSE");
    assertThat(houses.medianPrice()).isNull();
    assertThat(houses.listingCount()).isEqualTo(3);

    // Eight priced apartments clears it.
    assertThat(byType(forSale, "APARTMENT").medianPrice()).isEqualTo(19_000_000L);
  }

  @Test
  void reportsPerSquareMetreAgainstItsOwnSampleCount() {
    stubTypicalDatabase();

    CategoryStatistics forSale =
        category(new MarketStatisticsService(jdbcTemplate).getMarketStatistics(), "FOR_SALE");

    // 12 listings carry a price but only 11 also carry a floor area. Reporting the per-square-metre
    // figure against the larger count would overstate what it rests on.
    assertThat(forSale.pricedListingCount()).isEqualTo(12);
    assertThat(forSale.measuredListingCount()).isEqualTo(11);
    assertThat(forSale.medianPricePerSqm()).isEqualTo(137_500L);

    Breakdown houses = byType(forSale, "HOUSE");
    assertThat(houses.measuredListingCount()).isZero();
    assertThat(houses.medianPricePerSqm()).isNull();
  }

  @Test
  void reportsInventoryMixAsSharesOfTheWhole() {
    stubTypicalDatabase();

    MarketStatisticsResponse response =
        new MarketStatisticsService(jdbcTemplate).getMarketStatistics();

    assertThat(response.inventoryMix())
        .extracting(MarketStatisticsResponse.MixEntry::label)
        .containsExactly("APARTMENT", "HOUSE", "VILLA");
    assertThat(response.inventoryMix().get(0).share()).isEqualTo(14 / 20d);
    assertThat(response.currency()).isEqualTo("ETB");
    assertThat(response.generatedOn()).isNotNull();
  }

  @Test
  void servesAnEmptySetRatherThanFailingWhenAQueryBreaks() {
    when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
        .thenThrow(new RuntimeException("relation \"visible\" does not exist"));

    MarketStatisticsResponse response =
        new MarketStatisticsService(jdbcTemplate).getMarketStatistics();

    // The page renders its prose either way; a broken aggregate must not take the page down.
    assertThat(response.byCategory()).isEmpty();
    assertThat(response.totalListings()).isZero();
    assertThat(response.minimumSampleSize()).isEqualTo(MarketStatisticsService.MINIMUM_SAMPLE_SIZE);
  }

  @Test
  void toleratesTheNumericTypesJdbcActuallyReturns() {
    // percentile_cont comes back as Double, count(*) as Long, and a numeric column can arrive as
    // BigDecimal depending on driver and query. All three have to land on the same value.
    when(jdbcTemplate.queryForList(contains("lower_quartile"), any(Object[].class)))
        .thenReturn(
            List.of(
                row(
                    "category",
                    "FOR_SALE",
                    "listing_count",
                    9L,
                    "priced_count",
                    9L,
                    "median_price",
                    new BigDecimal("19000000.49"),
                    "lower_quartile",
                    1.0,
                    "upper_quartile",
                    2.0,
                    "measured_count",
                    9L,
                    "median_per_sqm",
                    137500.6)));

    MarketStatisticsResponse response =
        new MarketStatisticsService(jdbcTemplate).getMarketStatistics();
    CategoryStatistics forSale = category(response, "FOR_SALE");

    assertThat(forSale.listingCount()).isEqualTo(9);
    assertThat(forSale.medianPrice()).isEqualTo(19_000_000L);
    assertThat(forSale.medianPricePerSqm()).isEqualTo(137_501L);
  }
}
