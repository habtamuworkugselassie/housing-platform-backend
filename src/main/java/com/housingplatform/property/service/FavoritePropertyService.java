package com.housingplatform.property.service;

import com.housingplatform.property.dto.PropertyResponse;
import java.util.List;
import java.util.UUID;

public interface FavoritePropertyService {
  List<PropertyResponse> getFavorites(UUID userId);

  void addFavorite(UUID userId, UUID propertyId);

  void removeFavorite(UUID userId, UUID propertyId);
}
