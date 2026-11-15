package com.housingplatform.enquiry.service;

import com.housingplatform.enquiry.domain.ListingEnquiry;
import com.housingplatform.enquiry.dto.ListingEnquiryRequest;
import com.housingplatform.enquiry.dto.ListingEnquiryResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListingEnquiryService {

  ListingEnquiryResponse submit(ListingEnquiryRequest request);

  Page<ListingEnquiryResponse> adminList(ListingEnquiry.Status status, Pageable pageable);

  long countNew();

  ListingEnquiryResponse updateStatus(UUID id, ListingEnquiry.Status status);
}
