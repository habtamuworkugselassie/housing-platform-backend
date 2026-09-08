package com.housingplatform.property.api;

import com.housingplatform.property.dto.MarketStatisticsResponse;
import com.housingplatform.property.service.MarketStatisticsService;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Aggregate statistics over live listings, for the public market overview page.
 *
 * <p>Takes no parameters, by design. Everything it reports is already public — it is derived from
 * listings any visitor can browse — but a filterable version would let a caller narrow a bucket
 * until a median described one identifiable seller's asking price, which is not something an
 * anonymous endpoint should offer. The page needs the whole-market view and nothing else.
 *
 * <p>Also served with a public cache header: the response is identical for every caller and the
 * page it feeds is the one being crawled.
 */
@RestController
@RequestMapping("/api/v1/public/market-statistics")
@Tag(
    name = "Public market statistics",
    description = "Aggregate asking-price statistics over live listings (read-only)")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
public class PublicMarketStatisticsController {

  private final MarketStatisticsService marketStatisticsService;

  @GetMapping
  @Operation(
      summary = "Get market statistics",
      description =
          "Median asking prices, price per square metre and inventory mix over AVAILABLE listings"
              + " from APPROVED organizations. Asking prices on this platform, not transaction"
              + " prices. Buckets below the reported minimum sample size return null prices.")
  public ResponseEntity<MarketStatisticsResponse> getMarketStatistics() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(15)).cachePublic())
        .body(marketStatisticsService.getMarketStatistics());
  }
}
