package com.housingplatform.exhibition.service;

import com.housingplatform.exhibition.dto.ExhibitionInterestRequest;
import com.housingplatform.exhibition.dto.ExhibitionInterestResponse;

public interface ExhibitionInterestService {

  ExhibitionInterestResponse register(ExhibitionInterestRequest request);

  /**
   * Opts a registrant out of the reminder series, by the token in their unsubscribe link.
   *
   * <p>Idempotent: unsubscribing twice is a success both times, because a reader who clicks the
   * link again wants to be told they are out, not told the link is broken.
   *
   * @return false only when no registrant carries that token
   */
  boolean unsubscribe(String token);
}
