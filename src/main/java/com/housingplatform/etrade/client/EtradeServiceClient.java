package com.housingplatform.etrade.client;

import com.housingplatform.etrade.dto.EtradeBusinessDetails;
import java.util.Optional;

/** Port over the standalone etrade-service; see {@link EtradeServiceRestClient}. */
public interface EtradeServiceClient {

  /**
   * @param tin a normalized 10-digit TIN
   * @return the mapped record, or empty when eTrade knows no trader with that TIN (service 404)
   * @throws com.housingplatform.etrade.EtradeUnavailableException when the service or eTrade fails
   * @throws com.housingplatform.shared.exception.BusinessException when the service rejects the TIN
   */
  Optional<EtradeBusinessDetails> findBusiness(String tin, boolean includePhotos);
}
