package com.housingplatform.notification.service;

import com.housingplatform.notification.domain.Notification;
import com.housingplatform.notification.dto.NotificationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface NotificationMapper {
    NotificationResponse toResponse(Notification notification);
}
