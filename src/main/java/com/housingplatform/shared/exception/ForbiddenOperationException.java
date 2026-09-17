package com.housingplatform.shared.exception;

/**
 * Raised when an authenticated caller acts on a resource they do not own or manage. Maps to 403.
 */
public class ForbiddenOperationException extends RuntimeException {
  public ForbiddenOperationException(String message) {
    super(message);
  }
}
