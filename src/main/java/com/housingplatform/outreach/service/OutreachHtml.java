package com.housingplatform.outreach.service;

import com.housingplatform.shared.mail.MailLocale;

/**
 * HTML for an outreach email: the composed text as paragraphs inside the site's email shell,
 * optionally followed by the package table and a registration button. A plain-text part is always
 * sent alongside, so the text is the source of truth.
 */
public final class OutreachHtml {
  private OutreachHtml() {}

  public static String render(
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
    StringBuilder b = new StringBuilder(8000);
    b.append("<!DOCTYPE html><html lang=\"")
        .append(am ? "am" : "en")
        .append("\"><head><meta charset=\"UTF-8\">")
        .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        .append(
            "<meta name=\"color-scheme\" content=\"light\"><meta name=\"supported-color-schemes\" content=\"light\">")
        .append("</head><body style=\"margin:0;padding:0;background:#f4f2fa;font-family:")
        .append(font)
        .append(";color:#1f1535;\">")
        .append(
            "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:#f4f2fa;\"><tr><td align=\"center\" style=\"padding:24px 12px;\">")
        .append(
            "<table role=\"presentation\" width=\"640\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:640px;width:100%;\">")
        .append(
            "<tr><td bgcolor=\"#2e1065\" style=\"background:#2e1065;background-image:linear-gradient(#2e1065,#2e1065);border-radius:24px 24px 0 0;padding:24px 32px;\">")
        .append("<a href=\"")
        .append(esc(siteUrl))
        .append("\" style=\"text-decoration:none;\"><img src=\"")
        .append(esc(logoUrl))
        .append(
            "\" width=\"150\" alt=\"Ethio Build Connect\" style=\"display:block;width:150px;height:auto;border:0;\"></a></td></tr>")
        .append(
            "<tr><td style=\"background:#ffffff;padding:28px 32px 8px;font-size:15px;line-height:1.65;color:#2b2140;\">");
    for (String para : bodyText.trim().split("\\n\\s*\\n")) {
      b.append("<p style=\"margin:0 0 14px;white-space:pre-line;\">")
          .append(linkify(esc(para.trim())))
          .append("</p>");
    }
    b.append("</td></tr>");
    if (includePackages) {
      b.append("<tr><td style=\"background:#ffffff;padding:4px 32px 8px;\">")
          .append(
              "<p style=\"margin:0 0 10px;font-size:12px;letter-spacing:0.08em;text-transform:uppercase;color:#6d28d9;font-weight:700;\">")
          .append(esc(OutreachPackages.heading(locale)))
          .append("</p>")
          .append(
              "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"border:1px solid #e9e4f5;border-radius:16px;border-collapse:separate;overflow:hidden;\">");
      int i = 0;
      for (OutreachPackages.Tier t : OutreachPackages.tiers(locale)) {
        String bg = (i++ % 2 == 0) ? "#faf9fd" : "#ffffff";
        b.append("<tr><td style=\"padding:12px 16px;background:")
            .append(bg)
            .append(";vertical-align:top;\">")
            .append("<div style=\"font-weight:700;color:#1f1535;\">")
            .append(esc(t.name()))
            .append("</div>")
            .append("<div style=\"font-size:13px;color:#6b5f85;\">")
            .append(esc(t.highlight()))
            .append("</div></td>")
            .append("<td style=\"padding:12px 16px;background:")
            .append(bg)
            .append(";text-align:right;white-space:nowrap;vertical-align:top;\">")
            .append("<div style=\"font-weight:800;color:#2e1065;\">")
            .append(esc(t.price()))
            .append("</div>")
            .append("<div style=\"font-size:12px;color:#6b5f85;\">")
            .append(esc(t.booth()))
            .append("</div></td></tr>");
      }
      b.append("</table><p style=\"margin:10px 0 16px;font-size:12px;color:#6b5f85;\">")
          .append(esc(OutreachPackages.note(locale)))
          .append("</p>");
      b.append(
              "<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" style=\"margin:0 0 20px;\"><tr><td bgcolor=\"#f4c977\" style=\"background:#f4c977;background-image:linear-gradient(#f4c977,#f4c977);border-radius:999px;\">")
          .append("<a href=\"")
          .append(esc(registerUrl))
          .append(
              "\" style=\"display:inline-block;padding:12px 24px;font-size:14px;font-weight:700;color:#2e1065;text-decoration:none;\">")
          .append(am ? "በድረ-ገጹ ላይ ይመዝገቡ" : "Register on the portal")
          .append("</a></td></tr></table></td></tr>");
    }
    b.append(
            "<tr><td style=\"background:#ffffff;border-radius:0 0 24px 24px;padding:16px 32px 24px;border-top:1px solid #e9e4f5;font-size:12px;line-height:1.6;color:#6b5f85;\">")
        .append(
            "<strong style=\"color:#2e1065;\">Ethio Build Connect Expo</strong> · 9–13 January 2027 · Addis International Convention Center<br>")
        .append("<a href=\"")
        .append(esc(siteUrl))
        .append("\" style=\"color:#6d28d9;text-decoration:none;\">")
        .append(esc(siteUrl.replaceFirst("^https?://", "")))
        .append("</a></td></tr>")
        .append("</table></td></tr></table></body></html>");
    return b.toString();
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
