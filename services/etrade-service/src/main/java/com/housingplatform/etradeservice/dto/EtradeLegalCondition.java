package com.housingplatform.etradeservice.dto;

import java.util.Map;

/**
 * Legal form of the registered trader, as eTrade's {@code LegalCondtion} code. Names come from
 * eTrade's own {@code GET /api/LegalStatus/{lang}} list (spelling normalised); unknown codes are
 * passed through with a null name.
 */
public record EtradeLegalCondition(String code, String name, String nameAmharic) {

  private static final Map<String, String[]> NAMES =
      Map.ofEntries(
          Map.entry("1", new String[] {"Sole Proprietorship", "ግለሰብ ነጋዴ"}),
          Map.entry("2", new String[] {"Private Limited Company", "ሃላፊነቱ የተወሰነ የግል ማህበር"}),
          Map.entry("3", new String[] {"Share Company", "የአክሲዮን ማህበር"}),
          Map.entry("4", new String[] {"Commercial Representative", "የንግድ እንደራሴ"}),
          Map.entry("5", new String[] {"Public Enterprise", "መንግስታዊ ልማት ድርጅት"}),
          Map.entry("6", new String[] {"General Partnership", "የህብረት ሽርክና ማህበር"}),
          Map.entry("7", new String[] {"Cooperative Association", "የህብረት ስራ ማህበር"}),
          Map.entry("8", new String[] {"Private Enterprise", "የግል ኢንተርፕራይዝ"}),
          Map.entry("9", new String[] {"Trade Sectoral Association", "የንግድ ዘርፍ ማህበራት"}),
          Map.entry("10", new String[] {"Non-Public Enterprise", "መንግስታዊ ያልሆነ ልማት ድርጅት"}),
          Map.entry("11", new String[] {"NGO", "መንግስታዊ ያልሆነ የበጎ አድራጎት ድርጅት"}),
          Map.entry(
              "12",
              new String[] {"Branch of a Foreign Chamber of Commerce", "የውጪ ሃገር የንግድ ምክር ቤት"}),
          Map.entry("13", new String[] {"Holding Company", "የሆልዲንግ ኩባንያ"}),
          Map.entry("14", new String[] {"Franchising", "ፍሬንቻይዚንግ"}),
          Map.entry("15", new String[] {"Border Trade", "የጠረፍ ንግድ"}),
          Map.entry(
              "16", new String[] {"Joint and Limited Partnership", "ባለ ሁለት ሃላፊነት የህብረት ሽርክና ማህበር"}),
          Map.entry(
              "19",
              new String[] {
                "International Bid Winner (Foreign Company)", "ኢንተርናሽናል ጨረታ ያሸነፉ የውጪ ሃገር ድርጅቶች"
              }),
          Map.entry(
              "21",
              new String[] {
                "One-Member Private Limited Company", "ባለ አንድ አባል ሃላፊነቱ የተወሰነ የግል ማህበር"
              }),
          Map.entry(
              "22", new String[] {"Limited Liability Partnership", "ሃላፊነቱ የተወሰነ የሽርክና ማህበር"}));

  public static EtradeLegalCondition ofCode(String code) {
    if (code == null || code.isBlank()) {
      return null;
    }
    String key = code.trim();
    String[] names = NAMES.get(key);
    return new EtradeLegalCondition(
        key, names != null ? names[0] : null, names != null ? names[1] : null);
  }
}
