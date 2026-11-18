package com.housingplatform.outreach.service;

import com.housingplatform.outreach.domain.OutreachContact;
import com.housingplatform.outreach.domain.OutreachTemplate;
import com.housingplatform.outreach.domain.TemplateKey;
import com.housingplatform.shared.mail.MailLocale;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Fills a template's {{placeholders}} for one contact and appends the signature and the opt-out
 * line, so neither can be forgotten in an edited template.
 */
@Component
@RequiredArgsConstructor
public class OutreachTemplateRenderer {

  public record Rendered(String subject, String body) {}

  private final OutreachProperties props;

  public OutreachProperties props() {
    return props;
  }

  /** Portal registration link for this contact's language and role. */
  public String registerUrl(OutreachContact c) {
    boolean am = MailLocale.isAmharic(c.getLocale());
    String site = props.getSiteUrl().replaceAll("/$", "");
    return (am ? site + "/am" : site) + "/?interest=" + effectiveKey(c).interest();
  }

  public Rendered render(OutreachTemplate template, OutreachContact c, String firstSubject) {
    Map<String, String> v = values(c, firstSubject);
    String subject = fill(template.getSubject(), v);
    String body = fill(template.getBody(), v).trim() + "\n\n" + closing(c.getLocale());
    return new Rendered(subject, body);
  }

  Map<String, String> values(OutreachContact c, String firstSubject) {
    boolean am = MailLocale.isAmharic(c.getLocale());
    TemplateKey key = effectiveKey(c);
    String site = props.getSiteUrl().replaceAll("/$", "");
    String prefix = am ? site + "/am" : site;
    Map<String, String> v = new LinkedHashMap<>();
    v.put("name", nz(c.getName()));
    v.put("contact_name", contactName(c, am));
    v.put("project_or_company", nz(c.getName()));
    v.put("hook", nz(c.getHook()).isBlank() ? defaultHook(c, am) : c.getHook().trim());
    v.put("site_url", site);
    v.put("sponsorship_url", prefix + "/#sponsorship");
    v.put("register_url", prefix + "/?interest=" + key.interest());
    v.put("sender_name", props.getSenderName());
    v.put("sender_title", props.getSenderTitle());
    v.put("sender_phone", props.getSenderPhone());
    v.put("sender_email", props.getSenderEmail());
    v.put("first_subject", nz(firstSubject));
    return v;
  }

  public static TemplateKey effectiveKey(OutreachContact c) {
    if (c.getTemplateKey() != null && !c.getTemplateKey().isBlank()) {
      try {
        return TemplateKey.valueOf(c.getTemplateKey());
      } catch (IllegalArgumentException ignored) {
        // fall through to sector mapping
      }
    }
    return TemplateKey.forSector(c.getSector());
  }

  private static String contactName(OutreachContact c, boolean am) {
    String n = nz(c.getContactName()).trim();
    if (!n.isEmpty()) return n;
    return am ? "የ" + nz(c.getName()) + " ቡድን" : nz(c.getName()) + " team";
  }

  private static String defaultHook(OutreachContact c, boolean am) {
    return am
        ? "የ" + nz(c.getName()) + "ን ሥራ አይቼ በቀጥታ ላግኝዎ ፈለግሁ።"
        : "I came across " + nz(c.getName()) + "'s work and wanted to reach you directly.";
  }

  private String closing(String locale) {
    boolean am = MailLocale.isAmharic(locale);
    StringBuilder sb = new StringBuilder();
    sb.append(am ? "ከአክብሮት ጋር፣" : "Kind regards,").append('\n');
    String brand = am ? "ኢትዮ ቢልድ ኮኔክት" : "Ethio Build Connect";
    String name = props.getSenderName() == null ? "" : props.getSenderName().trim();
    boolean nameIsBrand =
        name.equalsIgnoreCase("Ethio Build Connect") || name.equals("ኢትዮ ቢልድ ኮኔክት");
    // A person signs with their title and the brand; when the sender is the brand itself,
    // write it once in the reader's language.
    if (nameIsBrand) {
      if (!props.getSenderTitle().isBlank()) sb.append(props.getSenderTitle()).append('\n');
      sb.append(brand).append('\n');
    } else {
      if (!name.isBlank()) sb.append(name).append('\n');
      if (!props.getSenderTitle().isBlank()) {
        sb.append(props.getSenderTitle()).append(am ? "፣ " : ", ").append(brand).append('\n');
      } else {
        sb.append(brand).append('\n');
      }
    }
    if (!props.getCompanyLine().isBlank()) sb.append(props.getCompanyLine()).append('\n');
    String contact =
        String.join(
            " · ",
            java.util.stream.Stream.of(props.getSenderPhone(), props.getSenderEmail())
                .filter(s -> s != null && !s.isBlank())
                .toList());
    if (!contact.isBlank()) sb.append(contact).append('\n');
    sb.append(props.getSiteUrl().replaceAll("^https?://", "").replaceAll("/$", "")).append("\n\n");
    sb.append(
        am
            ? "ስለ ኤክስፖው መልእክት መቀበል ካልፈለጉ \"አልፈልግም\" ብለው ይመልሱ፤ ዳግም አንጽፍልዎትም።"
            : "If you would rather not hear from us about the expo, reply \"no thanks\" and we will not write again.");
    return sb.toString();
  }

  private static String fill(String text, Map<String, String> v) {
    String out = text;
    for (Map.Entry<String, String> e : v.entrySet()) {
      out = out.replace("{{" + e.getKey() + "}}", e.getValue());
    }
    return out;
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }
}
