package com.housingplatform.identity.service;

import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.dto.UserResponse;
import com.housingplatform.identity.dto.UserUpdateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface UserMapper {
  UserResponse toResponse(User user);

  void updateEntity(@MappingTarget User user, UserUpdateRequest request);
}
