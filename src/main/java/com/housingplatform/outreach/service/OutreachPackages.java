package com.housingplatform.outreach.service;

import com.housingplatform.shared.mail.MailLocale;
import java.util.List;

/** The sponsorship tiers from the printed package sheet, for the email body. */
public final class OutreachPackages {
  private OutreachPackages() {}

  public record Tier(String name, String price, String booth, String highlight) {}

  public static List<Tier> tiers(String locale) {
    if (MailLocale.isAmharic(locale)) {
      return List.of(
          new Tier(
              "ልዩ ደረጃ ስፖንሰር (Exclusive)",
              "5,000,000 ብር",
              "54 ካሬ ሜትር",
              "ተባባሪ አዘጋጅ፣ የመክፈቻ ንግግር፣ ከሚኒስትሩ የተፈረመ የዕውቅና ሰርተፍኬት"),
          new Tier(
              "1ኛ ደረጃ · ፕላቲነም",
              "1,700,000 ብር",
              "27 ካሬ ሜትር",
              "ከሚኒስትሩ የተፈረመ ሰርተፍኬት፣ የፊት ገጽ ማስታወቂያ፣ 2,500 ብሮሸሮች"),
          new Tier(
              "2ኛ ደረጃ · ወርቅ", "900,000 ብር", "18 ካሬ ሜትር", "የውስጥ ገጽ ማስታወቂያ፣ 1,500 ብሮሸሮች፣ VIP ቦታ"),
          new Tier("3ኛ ደረጃ · ብር", "250,000 ብር", "9 ካሬ ሜትር", "ፕሪሚየም ቦታ፣ VIP ቦታ፣ የ1 ዓመት ዲጂታል መለጠፍ"),
          new Tier(
              "የልዩ ተሳታፊ ፓኬጅ", "100,000 ብር", "ዲጂታል ብቻ", "በድረ-ገጹ እና በመተግበሪያው ሙሉ መገለጫ፣ የራስዎ መግቢያ"));
    }
    return List.of(
        new Tier(
            "Exclusive sponsor",
            "ETB 5,000,000",
            "54 m² booth",
            "Co-organizer, opening speech, certificate signed by the Minister"),
        new Tier(
            "Tier 1 · Platinum",
            "ETB 1,700,000",
            "27 m² booth",
            "Certificate signed by the Minister, front-page advertising, 2,500 brochures"),
        new Tier(
            "Tier 2 · Gold",
            "ETB 900,000",
            "18 m² booth",
            "Inside-page advertising, 1,500 brochures, VIP seating"),
        new Tier(
            "Tier 3 · Silver",
            "ETB 250,000",
            "9 m² booth",
            "Prime location, VIP seating, one year of digital posting"),
        new Tier(
            "Special Participant Package",
            "ETB 100,000",
            "Digital only",
            "Full profile on the website and app with your own login"));
  }

  public static String heading(String locale) {
    return MailLocale.isAmharic(locale) ? "የስፖንሰርሺፕ ፓኬጆች" : "Sponsorship packages";
  }

  public static String note(String locale) {
    return MailLocale.isAmharic(locale)
        ? "ሁሉም ዋጋዎች ተ.እ.ታ አያካትቱም። ቦታዎች በመጡበት ቅደም ተከተል ይሰጣሉ። ሙሉ ዝርዝሩ በተያያዘው ሰነድ ውስጥ አለ።"
        : "All prices exclude VAT. Places are allocated first come, first served. The full detail is in the attached package sheet.";
  }

  /** Plain-text rendering for the text part. */
  public static String plainText(String locale) {
    StringBuilder sb = new StringBuilder(heading(locale)).append('\n');
    for (Tier t : tiers(locale)) {
      sb.append("- ")
          .append(t.name())
          .append(": ")
          .append(t.price())
          .append(", ")
          .append(t.booth())
          .append(". ")
          .append(t.highlight())
          .append('\n');
    }
    sb.append(note(locale)).append('\n');
    return sb.toString();
  }
}
