package com.housingplatform.loan.service;

import com.housingplatform.loan.domain.LoanApplication;
import com.housingplatform.loan.dto.LoanApplicationRequest;
import com.housingplatform.loan.dto.LoanApplicationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface LoanApplicationMapper {
  LoanApplication toEntity(LoanApplicationRequest request);

  LoanApplicationResponse toResponse(LoanApplication application);
}
