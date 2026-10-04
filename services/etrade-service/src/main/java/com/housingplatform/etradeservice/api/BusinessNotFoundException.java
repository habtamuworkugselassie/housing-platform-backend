package com.housingplatform.etradeservice.api;

/** eTrade has no registration under the TIN. */
public class BusinessNotFoundException extends RuntimeException {
  public BusinessNotFoundException(String tin) {
    super("No business is registered on eTrade under TIN " + tin);
  }
}
