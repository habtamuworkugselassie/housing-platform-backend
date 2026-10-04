package com.housingplatform.etradeservice.api;

import java.time.Instant;

/** Error body, shaped like the platform backend's so callers can share handling. */
public record ErrorResponse(
    Instant timestamp, int status, String error, String message, String path) {}
