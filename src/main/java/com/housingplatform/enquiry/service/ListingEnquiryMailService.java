package com.housingplatform.enquiry.service;

import com.housingplatform.enquiry.domain.ListingEnquiry;

public interface ListingEnquiryMailService {
  /** Receipt to the enquirer, the enquiry to the listing's company, and a copy to the team. */
  void sendEnquiryEmails(ListingEnquiry enquiry);
}
