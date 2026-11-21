package com.housingplatform.reservation.service;

import java.util.List;

/** Where sponsors pay. One list, used by the emails; the brochure carries the same accounts. */
public final class SponsorshipPaymentDetails {
  private SponsorshipPaymentDetails() {}

  public record Account(String bank, String bankAm, String number) {}

  public static final List<Account> ACCOUNTS =
      List.of(
          new Account("Commercial Bank of Ethiopia", "የኢትዮጵያ ንግድ ባንክ", "1000314847467"),
          new Account("Bank of Abyssinia", "አቢሲኒያ ባንክ", "58656283"),
          new Account("Awash Bank", "አዋሽ ባንክ", "01304814267901"),
          new Account("Cooperative Bank of Oromia", "የኦሮሚያ ህብረት ስራ ባንክ", "1062400139578"),
          new Account("Amhara Bank", "አማራ ባንክ", "9900003477154"),
          new Account("Dashen Bank", "ዳሽን ባንክ", "0264189810011"),
          new Account("Enat Bank", "እናት ባንክ", "0201124323140001"));

  public static final String PAYEE = "Dream Team Trading PLC";
}
