package com.housingplatform.etradeservice.api;

/** The caller's TIN is not ten digits. */
public class InvalidTinException extends RuntimeException {
  public InvalidTinException(String message) {
    super(message);
  }
}
