package com.housingplatform.property.service.impl;

import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.property.domain.FavoriteProperty;
import com.housingplatform.property.domain.Property;
import com.housingplatform.property.dto.PropertyResponse;
import com.housingplatform.property.repository.FavoritePropertyRepository;
import com.housingplatform.property.repository.PropertyRepository;
import com.housingplatform.property.service.FavoritePropertyService;
import com.housingplatform.property.service.PropertyMapper;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FavoritePropertyServiceImpl implements FavoritePropertyService {

  private final FavoritePropertyRepository favoritePropertyRepository;
  private final UserRepository userRepository;
  private final PropertyRepository propertyRepository;
  private final PropertyMapper propertyMapper;

  @Override
  @Transactional(readOnly = true)
  public List<PropertyResponse> getFavorites(UUID userId) {
    return favoritePropertyRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(FavoriteProperty::getProperty)
        .map(propertyMapper::toResponseWithImages)
        .toList();
  }

  @Override
  @Transactional
  public void addFavorite(UUID userId, UUID propertyId) {
    if (favoritePropertyRepository.existsByUserIdAndPropertyId(userId, propertyId)) {
      return;
    }
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    Property property =
        propertyRepository
            .findById(propertyId)
            .orElseThrow(() -> new ResourceNotFoundException("Property", propertyId));
    favoritePropertyRepository.save(
        FavoriteProperty.builder().user(user).property(property).build());
  }

  @Override
  @Transactional
  public void removeFavorite(UUID userId, UUID propertyId) {
    favoritePropertyRepository.deleteByUserIdAndPropertyId(userId, propertyId);
  }
}
