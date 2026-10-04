package com.housingplatform.etradeservice.api;

import com.housingplatform.etradeservice.EtradeUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {

  @ExceptionHandler(InvalidTinException.class)
  public ResponseEntity<ErrorResponse> invalidTin(InvalidTinException ex, HttpServletRequest req) {
    return body(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), req);
  }

  @ExceptionHandler(BusinessNotFoundException.class)
  public ResponseEntity<ErrorResponse> notFound(
      BusinessNotFoundException ex, HttpServletRequest req) {
    return body(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), req);
  }

  @ExceptionHandler(EtradeUnavailableException.class)
  public ResponseEntity<ErrorResponse> upstream(
      EtradeUnavailableException ex, HttpServletRequest req) {
    log.warn("eTrade upstream failure: {}", ex.getMessage());
    return body(HttpStatus.BAD_GATEWAY, "Upstream Service Unavailable", ex.getMessage(), req);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> unexpected(Exception ex, HttpServletRequest req) {
    log.error("Unhandled error on {}", req.getRequestURI(), ex);
    return body(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "Unexpected error", req);
  }

  private static ResponseEntity<ErrorResponse> body(
      HttpStatus status, String error, String message, HttpServletRequest req) {
    return ResponseEntity.status(status)
        .body(
            new ErrorResponse(Instant.now(), status.value(), error, message, req.getRequestURI()));
  }
}
