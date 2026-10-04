package com.housingplatform.etradeservice;

/**
 * eTrade could not be reached or answered with something other than a registration record. Mapped
 * to HTTP 502 so callers can tell "upstream is down" from "TIN not found" (404) and from "bad TIN"
 * (400).
 */
public class EtradeUnavailableException extends RuntimeException {

  public EtradeUnavailableException(String message) {
    super(message);
  }

  public EtradeUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
