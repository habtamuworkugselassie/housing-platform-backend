package com.housingplatform.outreach.service;

import com.housingplatform.shared.mail.MailLocale;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * HTML for an outreach email in the site's email shell: wordmark header, the subject as a title,
 * the composed text as paragraphs, an event facts strip, the package table with a registration
 * button, and the signature and opt-out line styled as a footer. The plain-text part is always sent
 * alongside, so the composed text stays the source of truth.
 */
public final class OutreachHtml {
  private OutreachHtml() {}

  public static String render(
      String subject,
      String bodyText,
      String locale,
      boolean includePackages,
      String registerUrl,
      String siteUrl,
      String logoUrl) {
    boolean am = MailLocale.isAmharic(locale);
    String font =
        am
            ? "'Noto Sans Ethiopic','Abyssinica SIL',Roboto,'Helvetica Neue',Arial,sans-serif"
            : "Roboto,'Helvetica Neue',Arial,sans-serif";
    List<String> paras = new ArrayList<>(Arrays.asList(bodyText.trim().split("\\n\\s*\\n")));
    // The renderer appends the opt-out line last and the signature before it; style them as a
    // footer.
    String optOut = paras.size() > 1 ? paras.remove(paras.size() - 1).trim() : "";
    String signature = paras.size() > 1 ? paras.remove(paras.size() - 1).trim() : "";

    StringBuilder b = new StringBuilder(10000);
    b.append("<!DOCTYPE html><html lang=\"")
        .append(am ? "am" : "en")
        .append("\"><head><meta charset=\"UTF-8\">")
        .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        .append(
            "<meta name=\"color-scheme\" content=\"light\"><meta name=\"supported-color-schemes\" content=\"light\">")
        .append("<title>")
        .append(esc(subject))
        .append("</title></head>")
        .append("<body style=\"margin:0;padding:0;background:#f4f2fa;font-family:")
        .append(font)
        .append(";color:#1f1535;\">")
        .append(
            "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:#f4f2fa;\"><tr><td align=\"center\" style=\"padding:24px 12px;\">")
        .append(
            "<table role=\"presentation\" width=\"640\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:640px;width:100%;\">");
    // header
    b.append(
            "<tr><td bgcolor=\"#2e1065\" style=\"background:#2e1065;background-image:linear-gradient(#2e1065,#2e1065);border-radius:24px 24px 0 0;padding:26px 32px;\">")
        .append("<a href=\"")
        .append(esc(siteUrl))
        .append("\" style=\"text-decoration:none;\"><img src=\"")
        .append(esc(logoUrl))
        .append(
            "\" width=\"160\" alt=\"Ethio Build Connect\" style=\"display:block;width:160px;height:auto;border:0;\"></a>")
        .append(
            "<p style=\"margin:14px 0 0;font-size:11px;letter-spacing:0.16em;text-transform:uppercase;color:#c4b5fd;font-weight:700;\">")
        .append(am ? "ኢትዮ ቢልድ ኮኔክት ኤክስፖ 2027" : "Ethio Build Connect Expo 2027")
        .append("</p></td></tr>");
    // title + text
    b.append("<tr><td style=\"background:#ffffff;padding:30px 32px 6px;\">")
        .append(
            "<h1 style=\"margin:0 0 18px;font-size:24px;line-height:1.25;font-weight:800;letter-spacing:-0.01em;color:#1f1535;\">")
        .append(esc(subject))
        .append("</h1>");
    for (String para : paras) {
      String p = para.trim();
      if (p.isEmpty()) continue;
      if (p.startsWith("- ") || p.startsWith("1. ") || p.startsWith("• ")) {
        b.append(
            "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"margin:0 0 16px;\">");
        for (String line : p.split("\n")) {
          String item = line.replaceFirst("^(- |• |\\d+\\. )", "").trim();
          if (item.isEmpty()) continue;
          b.append(
                  "<tr><td width=\"22\" valign=\"top\" style=\"padding:4px 0;color:#f4c977;font-size:15px;\">&#9670;</td>")
              .append("<td style=\"padding:4px 0;font-size:15px;line-height:1.55;color:#2b2140;\">")
              .append(linkify(esc(item)))
              .append("</td></tr>");
        }
        b.append("</table>");
      } else {
        b.append(
                "<p style=\"margin:0 0 14px;font-size:15px;line-height:1.65;color:#2b2140;white-space:pre-line;\">")
            .append(linkify(esc(p)))
            .append("</p>");
      }
    }
    b.append("</td></tr>");
    // event facts strip
    b.append("<tr><td style=\"background:#ffffff;padding:6px 32px 10px;\">")
        .append(
            "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"border-collapse:separate;border-radius:16px;overflow:hidden;\"><tr>")
        .append(
            fact(
                am ? "ቀን" : "Date",
                am ? "ጃንዋሪ 9–13, 2027 (ጥር 1–5, 2019 ዓ.ም)" : "9–13 January 2027"))
        .append(
            fact(
                am ? "ቦታ" : "Venue",
                am ? "አዲስ ኢንተርናሽናል ኮንቬንሽን ሴንተር" : "Addis International Convention Center"))
        .append(
            fact(
                am ? "ቅርጸት" : "Format",
                am ? "5 ቀናት · 70–90 ስታንዶች · 365 ቀን መተግበሪያ" : "5 days · 70–90 booths · 365-day app"))
        .append("</tr></table></td></tr>");
    // packages
    if (includePackages) {
      b.append("<tr><td style=\"background:#ffffff;padding:10px 32px 8px;\">")
          .append(
              "<p style=\"margin:0 0 10px;font-size:12px;letter-spacing:0.08em;text-transform:uppercase;color:#6d28d9;font-weight:700;\">")
          .append(esc(OutreachPackages.heading(locale)))
          .append("</p>")
          .append(
              "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"border:1px solid #e9e4f5;border-radius:16px;border-collapse:separate;overflow:hidden;\">");
      int i = 0;
      for (OutreachPackages.Tier t : OutreachPackages.tiers(locale)) {
        String bg = i == 0 ? "#2e1065" : (i % 2 == 1 ? "#faf9fd" : "#ffffff");
        String fg = i == 0 ? "#ffffff" : "#1f1535";
        String sub = i == 0 ? "#c4b5fd" : "#6b5f85";
        String price = i == 0 ? "#f4c977" : "#2e1065";
        b.append("<tr><td style=\"padding:12px 16px;background:")
            .append(bg)
            .append(";vertical-align:top;\">")
            .append("<div style=\"font-weight:700;color:")
            .append(fg)
            .append(";\">")
            .append(esc(t.name()))
            .append("</div>")
            .append("<div style=\"font-size:13px;color:")
            .append(sub)
            .append(";\">")
            .append(esc(t.highlight()))
            .append("</div></td>")
            .append("<td style=\"padding:12px 16px;background:")
            .append(bg)
            .append(";text-align:right;white-space:nowrap;vertical-align:top;\">")
            .append("<div style=\"font-weight:800;color:")
            .append(price)
            .append(";\">")
            .append(esc(t.price()))
            .append("</div>")
            .append("<div style=\"font-size:12px;color:")
            .append(sub)
            .append(";\">")
            .append(esc(t.booth()))
            .append("</div></td></tr>");
        i++;
      }
      b.append("</table><p style=\"margin:10px 0 16px;font-size:12px;color:#6b5f85;\">")
          .append(esc(OutreachPackages.note(locale)))
          .append("</p>");
      b.append(
              "<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" style=\"margin:0 0 8px;\"><tr><td bgcolor=\"#f4c977\" style=\"background:#f4c977;background-image:linear-gradient(#f4c977,#f4c977);border-radius:999px;\">")
          .append("<a href=\"")
          .append(esc(registerUrl))
          .append(
              "\" style=\"display:inline-block;padding:13px 26px;font-size:14px;font-weight:700;color:#2e1065;text-decoration:none;\">")
          .append(am ? "በድረ-ገጹ ላይ ይመዝገቡ" : "Register on the portal")
          .append("</a></td>")
          .append("<td style=\"padding-left:14px;font-size:13px;\"><a href=\"")
          .append(esc(siteUrl))
          .append(am ? "/am/#sponsorship" : "/#sponsorship")
          .append("\" style=\"color:#6d28d9;text-decoration:none;font-weight:600;\">")
          .append(am ? "ፓኬጆችን በድረ-ገጹ ላይ ይመልከቱ" : "See the packages online")
          .append("</a></td></tr></table>")
          .append("</td></tr>");
    }
    // signature
    if (!signature.isEmpty()) {
      b.append("<tr><td style=\"background:#ffffff;padding:16px 32px 8px;\">")
          .append(
              "<p style=\"margin:0;font-size:15px;line-height:1.6;color:#2b2140;white-space:pre-line;\">")
          .append(linkify(esc(signature)))
          .append("</p></td></tr>");
    }
    // footer with opt-out
    b.append(
            "<tr><td style=\"background:#ffffff;border-radius:0 0 24px 24px;padding:18px 32px 26px;border-top:1px solid #e9e4f5;font-size:12px;line-height:1.6;color:#6b5f85;\">")
        .append("<strong style=\"color:#2e1065;\">Ethio Build Connect</strong> · ")
        .append(
            am
                ? "የኢትዮጵያ የሪል እስቴት እና የግንባታ ገበያ እና ኤግዚቢሽን"
                : "Ethiopia's real estate & construction marketplace and expo")
        .append("<br>")
        .append("<a href=\"")
        .append(esc(siteUrl))
        .append("\" style=\"color:#6d28d9;text-decoration:none;\">")
        .append(esc(siteUrl.replaceFirst("^https?://", "")))
        .append("</a>");
    if (!optOut.isEmpty())
      b.append("<br><span style=\"color:#8b7fa6;\">").append(esc(optOut)).append("</span>");
    b.append("</td></tr></table></td></tr></table></body></html>");
    return b.toString();
  }

  private static String fact(String label, String value) {
    return "<td width=\"33%\" style=\"background:#f4f2fa;padding:12px 14px;vertical-align:top;\">"
        + "<div style=\"font-size:10px;letter-spacing:0.12em;text-transform:uppercase;color:#6d28d9;font-weight:700;\">"
        + esc(label)
        + "</div>"
        + "<div style=\"margin-top:3px;font-size:13px;font-weight:700;color:#1f1535;line-height:1.35;\">"
        + esc(value)
        + "</div></td>";
  }

  private static String linkify(String escaped) {
    return escaped.replaceAll(
        "(https?://[^\\s<]+)", "<a href=\"$1\" style=\"color:#6d28d9;\">$1</a>");
  }

  static String esc(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
