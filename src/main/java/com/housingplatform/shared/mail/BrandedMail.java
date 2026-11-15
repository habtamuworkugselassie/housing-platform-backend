package com.housingplatform.shared.mail;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/**
 * One transactional email in the site's design: violet header with the wordmark, a white card with
 * a title, paragraphs, an optional detail table and a gold button, and a quiet footer. Rendered as
 * both plain text and HTML so every client shows something sensible.
 */
@Getter
public final class BrandedMail {

  public record Detail(String label, String value) {}

  private final List<String> to = new ArrayList<>();
  private String replyTo;
  private String subject;
  private String preheader;
  private String title;
  private String greeting;
  private final List<String> paragraphs = new ArrayList<>();
  private final List<Detail> details = new ArrayList<>();
  private String messageLabel;
  private String messageBody;
  private String ctaLabel;
  private String ctaUrl;
  private final List<String> closing = new ArrayList<>();
  private String lang = MailLocale.EN;

  private BrandedMail() {}

  public static BrandedMail create() {
    return new BrandedMail();
  }

  /** Language of the copy: sets the html lang, the footer tagline and the font stack. */
  public BrandedMail lang(String lang) {
    this.lang = MailLocale.normalize(lang);
    return this;
  }

  public BrandedMail to(String... addresses) {
    for (String a : addresses) if (notBlank(a)) to.add(a.trim());
    return this;
  }

  public BrandedMail replyTo(String address) {
    this.replyTo = notBlank(address) ? address.trim() : null;
    return this;
  }

  public BrandedMail subject(String subject) {
    this.subject = subject;
    return this;
  }

  /** The one-line preview some clients show next to the subject. */
  public BrandedMail preheader(String preheader) {
    this.preheader = preheader;
    return this;
  }

  public BrandedMail title(String title) {
    this.title = title;
    return this;
  }

  public BrandedMail greeting(String greeting) {
    this.greeting = greeting;
    return this;
  }

  public BrandedMail paragraph(String text) {
    if (notBlank(text)) paragraphs.add(text);
    return this;
  }

  public BrandedMail detail(String label, String value) {
    if (notBlank(value)) details.add(new Detail(label, value.trim()));
    return this;
  }

  /** A free-text block (the visitor's message) shown as a quoted panel. */
  public BrandedMail message(String label, String body) {
    if (notBlank(body)) {
      this.messageLabel = label;
      this.messageBody = body.trim();
    }
    return this;
  }

  public BrandedMail cta(String label, String url) {
    this.ctaLabel = label;
    this.ctaUrl = url;
    return this;
  }

  /** Lines after the button: what happens next, who to contact. */
  public BrandedMail closing(String text) {
    if (notBlank(text)) closing.add(text);
    return this;
  }

  // ------------------------------------------------------------------ rendering

  public String toPlainText(String siteUrl) {
    StringBuilder sb = new StringBuilder();
    if (notBlank(greeting)) sb.append(greeting).append("\n\n");
    for (String p : paragraphs) sb.append(p).append("\n\n");
    for (Detail d : details) sb.append(d.label()).append(": ").append(d.value()).append('\n');
    if (!details.isEmpty()) sb.append('\n');
    if (messageBody != null) {
      sb.append(messageLabel).append(":\n").append(messageBody).append("\n\n");
    }
    if (notBlank(ctaUrl)) sb.append(ctaLabel).append(": ").append(ctaUrl).append("\n\n");
    for (String c : closing) sb.append(c).append("\n\n");
    sb.append("Ethio Build Connect\n").append(siteUrl).append('\n').append(city()).append('\n');
    return sb.toString();
  }

  public String toHtml(String siteUrl, String logoUrl) {
    StringBuilder b = new StringBuilder(6000);
    b.append("<!DOCTYPE html><html lang=\"")
        .append(lang)
        .append("\"><head><meta charset=\"UTF-8\">")
        .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        .append(
            "<meta name=\"color-scheme\" content=\"light\"><meta name=\"supported-color-schemes\" content=\"light\">")
        .append("<title>")
        .append(esc(subject))
        .append("</title></head>")
        .append(
            "<body style=\"margin:0;padding:0;background:#f4f2fa;font-family:"
                + fontStack()
                + ";color:#1f1535;\">");
    if (notBlank(preheader)) {
      b.append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;color:#f4f2fa;\">")
          .append(esc(preheader))
          .append("</div>");
    }
    b.append(
            "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:#f4f2fa;\"><tr><td align=\"center\" style=\"padding:24px 12px;\">")
        .append(
            "<table role=\"presentation\" width=\"600\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:600px;width:100%;\">");
    // header
    b.append(
            "<tr><td bgcolor=\"#2e1065\" style=\"background:#2e1065;background-image:linear-gradient(#2e1065,#2e1065);border-radius:24px 24px 0 0;padding:26px 32px;\">")
        .append("<a href=\"")
        .append(esc(siteUrl))
        .append("\" style=\"text-decoration:none;\"><img src=\"")
        .append(esc(logoUrl))
        .append(
            "\" width=\"168\" alt=\"Ethio Build Connect\" style=\"display:block;width:168px;height:auto;border:0;\"></a>")
        .append("</td></tr>");
    // card
    b.append("<tr><td style=\"background:#ffffff;padding:32px 32px 8px;\">");
    if (notBlank(title)) {
      b.append(
              "<h1 style=\"margin:0 0 16px;font-size:24px;line-height:1.25;font-weight:800;letter-spacing:-0.01em;color:#1f1535;\">")
          .append(esc(title))
          .append("</h1>");
    }
    if (notBlank(greeting)) b.append(para(greeting));
    for (String p : paragraphs) b.append(para(p));
    if (!details.isEmpty()) {
      b.append(
          "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"margin:8px 0 20px;border:1px solid #e9e4f5;border-radius:16px;border-collapse:separate;overflow:hidden;\">");
      int i = 0;
      for (Detail d : details) {
        String bg = (i++ % 2 == 0) ? "#faf9fd" : "#ffffff";
        b.append("<tr><td style=\"padding:10px 16px;background:")
            .append(bg)
            .append(
                ";font-size:12px;letter-spacing:0.06em;text-transform:uppercase;color:#6b5f85;width:34%;vertical-align:top;\">")
            .append(esc(d.label()))
            .append("</td><td style=\"padding:10px 16px;background:")
            .append(bg)
            .append(";font-size:15px;color:#1f1535;word-break:break-word;\">")
            .append(linkify(d.value()))
            .append("</td></tr>");
      }
      b.append("</table>");
    }
    if (messageBody != null) {
      b.append(
              "<p style=\"margin:0 0 6px;font-size:12px;letter-spacing:0.06em;text-transform:uppercase;color:#6b5f85;\">")
          .append(esc(messageLabel))
          .append("</p>")
          .append(
              "<div style=\"margin:0 0 20px;padding:16px 18px;background:#f4f2fa;border-left:4px solid #f4c977;border-radius:0 14px 14px 0;font-size:15px;line-height:1.6;white-space:pre-wrap;\">")
          .append(esc(messageBody))
          .append("</div>");
    }
    if (notBlank(ctaUrl)) {
      b.append(
              "<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" style=\"margin:4px 0 24px;\"><tr><td bgcolor=\"#f4c977\" style=\"background:#f4c977;background-image:linear-gradient(#f4c977,#f4c977);border-radius:999px;\">")
          .append("<a href=\"")
          .append(esc(ctaUrl))
          .append(
              "\" style=\"display:inline-block;padding:13px 26px;font-size:15px;font-weight:700;color:#2e1065;text-decoration:none;border-radius:999px;\">")
          .append(esc(ctaLabel))
          .append("</a></td></tr></table>");
    }
    for (String c : closing) b.append(para(c));
    b.append("</td></tr>");
    // footer
    b.append(
            "<tr><td style=\"background:#ffffff;border-radius:0 0 24px 24px;padding:20px 32px 28px;border-top:1px solid #e9e4f5;font-size:12px;line-height:1.6;color:#6b5f85;\">")
        .append("<strong style=\"color:#2e1065;\">Ethio Build Connect</strong><br>")
        .append(esc(tagline()))
        .append("<br>")
        .append(esc(city()))
        .append(" &middot; <a href=\"")
        .append(esc(siteUrl))
        .append("\" style=\"color:#6d28d9;text-decoration:none;\">")
        .append(esc(siteUrl.replaceFirst("^https?://", "")))
        .append("</a>")
        .append("</td></tr>");
    b.append("</table></td></tr></table></body></html>");
    return b.toString();
  }

  private String fontStack() {
    return MailLocale.isAmharic(lang)
        ? "'Noto Sans Ethiopic','Abyssinica SIL','Nyala',Roboto,'Helvetica Neue',Arial,sans-serif"
        : "Roboto,'Helvetica Neue',Arial,sans-serif";
  }

  private String tagline() {
    return MailLocale.isAmharic(lang)
        ? "የኢትዮጵያ የሪል እስቴት እና የግንባታ ገበያ እና ኤግዚቢሽን"
        : "Ethiopia's real estate & construction marketplace and expo";
  }

  private String city() {
    return MailLocale.isAmharic(lang) ? "አዲስ አበባ፣ ኢትዮጵያ" : "Addis Ababa, Ethiopia";
  }

  private static String para(String text) {
    return "<p style=\"margin:0 0 16px;font-size:15px;line-height:1.6;color:#3b3252;\">"
        + esc(text)
        + "</p>";
  }

  private static String linkify(String value) {
    String v = esc(value);
    if (value.startsWith("http://") || value.startsWith("https://")) {
      return "<a href=\"" + v + "\" style=\"color:#6d28d9;\">" + v + "</a>";
    }
    if (value.contains("@") && !value.contains(" ")) {
      return "<a href=\"mailto:" + v + "\" style=\"color:#6d28d9;\">" + v + "</a>";
    }
    return v;
  }

  static String esc(String s) {
    if (s == null) return "";
    StringBuilder sb = new StringBuilder(s.length() + 16);
    for (char c : s.toCharArray()) {
      switch (c) {
        case '&' -> sb.append("&amp;");
        case '<' -> sb.append("&lt;");
        case '>' -> sb.append("&gt;");
        case '"' -> sb.append("&quot;");
        case '\'' -> sb.append("&#39;");
        default -> sb.append(c);
      }
    }
    return sb.toString();
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }
}
