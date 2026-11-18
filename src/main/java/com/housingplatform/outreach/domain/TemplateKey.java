package com.housingplatform.outreach.domain;

import java.util.Locale;

/** Which template a contact gets, derived from its sector unless overridden. */
public enum TemplateKey {
  REAL_ESTATE,
  BANK,
  INSURANCE,
  CONTRACTOR,
  ASSOCIATION,
  MEDIA,
  CONSULTANCY,
  /** Step 2 for every sector. */
  FOLLOW_UP;

  public static TemplateKey forSector(String sector) {
    String s = sector == null ? "" : sector.toLowerCase(Locale.ROOT);
    if (s.contains("real estate")) return REAL_ESTATE;
    if (s.contains("bank")) return BANK;
    if (s.contains("insur")) return INSURANCE;
    if (s.contains("media")) return MEDIA;
    if (s.contains("consult") || s.contains("architect") || s.contains("engineering")) {
      return CONSULTANCY;
    }
    if (s.contains("chamber")
        || s.contains("association")
        || s.contains("government")
        || s.contains("event")
        || s.contains("venue")) {
      return ASSOCIATION;
    }
    return CONTRACTOR;
  }

  /** Who the register link should sign them up as. */
  public String interest() {
    return switch (this) {
      case REAL_ESTATE, CONTRACTOR, CONSULTANCY -> "exhibitor";
      default -> "partner";
    };
  }
}
