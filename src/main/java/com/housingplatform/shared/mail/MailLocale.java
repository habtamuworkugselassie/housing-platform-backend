package com.housingplatform.shared.mail;

/** The languages receipts are written in. Anything unknown is English. */
public final class MailLocale {
  public static final String EN = "en";
  public static final String AM = "am";

  private MailLocale() {}

  public static String normalize(String locale) {
    if (locale == null) return EN;
    String l = locale.trim().toLowerCase();
    return l.startsWith(AM) ? AM : EN;
  }

  public static boolean isAmharic(String locale) {
    return AM.equals(normalize(locale));
  }
}
