package com.housingplatform.enquiry.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Public enquiry form. Exactly one of propertyId / organizationId is expected. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingEnquiryRequest {

  private UUID propertyId;
  private UUID organizationId;

  @NotBlank(message = "Name is required")
  @Size(max = 200)
  private String name;

  @NotBlank(message = "Email is required")
  @Email(message = "Invalid email format")
  @Size(max = 255)
  private String email;

  @Size(max = 50)
  private String phoneNumber;

  @NotBlank(message = "Message is required")
  @Size(max = 4000, message = "Message is too long")
  private String message;

  @Size(max = 1000)
  private String pageUrl;

  /** Honeypot: real users never fill it. A filled value is accepted but dropped. */
  private String website;
}
