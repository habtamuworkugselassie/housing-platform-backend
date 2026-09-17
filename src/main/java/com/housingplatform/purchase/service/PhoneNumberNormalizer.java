package com.housingplatform.purchase.service;

import com.housingplatform.shared.exception.BusinessException;
import java.util.regex.Pattern;

/**
 * Normalises the contact phone captured on a purchase order to E.164. Ethiopian local forms ({@code
 * 09…}, {@code 07…}, {@code 2519…}) are expanded to {@code +251…}; any other well-formed
 * international number is accepted as given.
 */
public final class PhoneNumberNormalizer {

  private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
  private static final Pattern ETHIOPIAN_LOCAL = Pattern.compile("^0?([79][0-9]{8})$");
  private static final Pattern ETHIOPIAN_INTL_NO_PLUS = Pattern.compile("^251([79][0-9]{8})$");

  private PhoneNumberNormalizer() {}

  public static String toE164(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new BusinessException("Contact phone number is required");
    }
    String digits = raw.trim().replaceAll("[\\s\\-().]", "");
    if (digits.startsWith("00")) {
      digits = "+" + digits.substring(2);
    }

    var local = ETHIOPIAN_LOCAL.matcher(digits);
    if (local.matches()) {
      return "+251" + local.group(1);
    }
    var intl = ETHIOPIAN_INTL_NO_PLUS.matcher(digits);
    if (intl.matches()) {
      return "+251" + intl.group(1);
    }
    if (E164.matcher(digits).matches()) {
      return digits;
    }
    throw new BusinessException(
        "Contact phone number '"
            + raw
            + "' is not a valid phone number. Use +2519XXXXXXXX or 09XXXXXXXX");
  }
}
