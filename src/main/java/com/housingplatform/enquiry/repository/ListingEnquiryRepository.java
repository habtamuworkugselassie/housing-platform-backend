package com.housingplatform.enquiry.repository;

import com.housingplatform.enquiry.domain.ListingEnquiry;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ListingEnquiryRepository extends JpaRepository<ListingEnquiry, UUID> {

  Page<ListingEnquiry> findAllByOrderByCreatedAtDesc(Pageable pageable);

  Page<ListingEnquiry> findAllByStatusOrderByCreatedAtDesc(
      ListingEnquiry.Status status, Pageable pageable);

  long countByStatus(ListingEnquiry.Status status);
}
