package com.housingplatform.enquiry.dto;

import com.housingplatform.enquiry.domain.ListingEnquiry;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateListingEnquiryStatusRequest {
  @NotNull private ListingEnquiry.Status status;
}
