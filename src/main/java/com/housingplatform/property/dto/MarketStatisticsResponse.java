package com.housingplatform.property.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Aggregate statistics over the platform's live listings.
 *
 * <p>These are <strong>asking prices published on this platform</strong>, not transaction prices
 * and not a market index. The distinction is not pedantry: an asking price is what a seller hopes
 * for, and describing a median of them as "the market price" would be a claim this data cannot
 * support. Every label the frontend renders says so.
 *
 * <p>Prices are whole Ethiopian Birr. Medians rather than means throughout, because property prices
 * are strongly right-skewed and a handful of high-end villas move a mean somewhere no actual
 * listing sits.
 *
 * <p>A price field is null wherever the bucket held fewer than {@link #minimumSampleSize} priced
 * listings. Null means "not enough listings to say", and the frontend prints that rather than a
 * number — a median over two asking prices is not a statistic, and publishing one as though it were
 * would be the fastest way to lose the credibility this page is meant to build.
 */
public record MarketStatisticsResponse(
    LocalDate generatedOn,
    String currency,
    int minimumSampleSize,
    int totalListings,
    List<CategoryStatistics> byCategory,
    List<MixEntry> inventoryMix,
    List<MixEntry> constructionMix) {

  /** Everything for one side of the market; sale and rental prices are never pooled. */
  public record CategoryStatistics(
      String category,
      int listingCount,
      int pricedListingCount,
      Long medianPrice,
      Long lowerQuartilePrice,
      Long upperQuartilePrice,
      int measuredListingCount,
      Long medianPricePerSqm,
      List<Breakdown> byType,
      List<Breakdown> byCity) {}

  /**
   * One row of a breakdown.
   *
   * <p>{@code measuredListingCount} counts only listings that published a floor area, which is why
   * it is reported next to the per-square-metre figure rather than left implicit: the two counts
   * differ, often widely, and a reader deserves to know which of them a number rests on.
   */
  public record Breakdown(
      String label,
      int listingCount,
      Long medianPrice,
      int measuredListingCount,
      Long medianPricePerSqm) {}

  /** A share-of-inventory row. {@code share} is a fraction of the total, not a percentage. */
  public record MixEntry(String label, int listingCount, double share) {}
}
