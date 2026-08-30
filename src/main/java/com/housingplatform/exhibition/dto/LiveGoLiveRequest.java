package com.housingplatform.exhibition.dto;

import jakarta.validation.constraints.NotBlank;

/** Anonymous/self request to go live; held as REQUESTED until an organizer approves. */
public record LiveGoLiveRequest(
    @NotBlank String name,
    String email,
    /** VISITOR | EXHIBITOR | ORGANIZER */
    String role,
    String company,
    @NotBlank String title) {}
