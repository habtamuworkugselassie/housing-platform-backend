package com.housingplatform.etradeservice.client;

import java.util.Optional;

/** Thin port over the eTrade registration API; see {@link EtradeRestClient}. */
public interface EtradeClient {

  /**
   * Fetches the registration record for a TIN.
   *
   * @param tin a normalized 10-digit TIN
   * @return the record, or empty when eTrade knows no trader with that TIN (HTTP 204)
   * @throws com.housingplatform.etradeservice.EtradeUnavailableException on transport or upstream
   *     errors
   */
  Optional<EtradeRegistrationResponse> findRegistrationByTin(String tin);
}
