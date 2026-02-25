package com.housingplatform.shared.domain;

public enum Currency {
  ETB("ETB", "Birr", "ብ"),
  USD("USD", "Dollar", "$");

  private final String code;
  private final String name;
  private final String symbol;

  Currency(String code, String name, String symbol) {
    this.code = code;
    this.name = name;
    this.symbol = symbol;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public String getSymbol() {
    return symbol;
  }

  public static Currency fromCode(String code) {
    if (code == null) {
      return ETB; // Default to ETB
    }
    for (Currency currency : values()) {
      if (currency.code.equalsIgnoreCase(code)) {
        return currency;
      }
    }
    return ETB; // Default to ETB if not found
  }
}
