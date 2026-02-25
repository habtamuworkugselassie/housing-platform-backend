package com.housingplatform.payment.service;

import com.housingplatform.payment.domain.Payment;
import com.housingplatform.payment.dto.PaymentRequest;
import com.housingplatform.payment.dto.PaymentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PaymentMapper {
  Payment toEntity(PaymentRequest request);

  PaymentResponse toResponse(Payment payment);
}
