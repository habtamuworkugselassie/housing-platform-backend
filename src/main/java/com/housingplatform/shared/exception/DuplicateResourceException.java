package com.housingplatform.shared.exception;

/**
 * Raised when a request would create a second copy of something that must be unique. Maps to 409.
 */
public class DuplicateResourceException extends RuntimeException {
  public DuplicateResourceException(String message) {
    super(message);
  }
}
