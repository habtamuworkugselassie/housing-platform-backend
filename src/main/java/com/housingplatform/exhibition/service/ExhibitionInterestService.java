package com.housingplatform.exhibition.service;

import com.housingplatform.exhibition.dto.ExhibitionInterestRequest;
import com.housingplatform.exhibition.dto.ExhibitionInterestResponse;

public interface ExhibitionInterestService {

  ExhibitionInterestResponse register(ExhibitionInterestRequest request);
}
