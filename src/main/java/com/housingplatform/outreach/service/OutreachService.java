package com.housingplatform.outreach.service;

import com.housingplatform.outreach.domain.OutreachContact;
import com.housingplatform.outreach.domain.OutreachMessage;
import com.housingplatform.outreach.domain.OutreachTemplate;
import com.housingplatform.outreach.domain.TemplateKey;
import com.housingplatform.outreach.dto.OutreachDtos.*;
import com.housingplatform.outreach.repository.OutreachContactRepository;
import com.housingplatform.outreach.repository.OutreachMessageRepository;
import com.housingplatform.outreach.repository.OutreachTemplateRepository;
import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import com.housingplatform.shared.mail.MailLocale;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutreachService {

  private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

  /** Statuses an import must never overwrite: the contact told us something. */
  private static final Set<OutreachContact.Status> PROTECTED =
      EnumSet.of(
          OutreachContact.Status.OPTED_OUT,
          OutreachContact.Status.REPLIED,
          OutreachContact.Status.REGISTERED,
          OutreachContact.Status.BOUNCED);

  private final OutreachContactRepository contacts;
  private final OutreachTemplateRepository templates;
  private final OutreachMessageRepository messages;
  private final OutreachTemplateRenderer renderer;
  private final OutreachMailer mailer;

  // ------------------------------------------------------------------ import

  /**
   * CSV with the prospect-list header:
   * name,sector,email,phone,website,city,confidence,source_url,notes[,locale,hook,contact_name,owner].
   */
  @Transactional
  public ImportResult importCsv(java.io.InputStream in) throws IOException {
    List<Map<String, String>> rows = parseCsv(new InputStreamReader(in, StandardCharsets.UTF_8));
    int added = 0, updated = 0, invalid = 0, protectedCount = 0;
    List<String> problems = new ArrayList<>();
    for (Map<String, String> r : rows) {
      String name = r.getOrDefault("name", "").trim();
      String email = r.getOrDefault("email", "").trim().toLowerCase(Locale.ROOT);
      if (name.isEmpty() || !EMAIL.matcher(email).matches()) {
        invalid++;
        if (!name.isEmpty() && problems.size() < 50) problems.add(name + ": no valid email");
        continue;
      }
      Optional<OutreachContact> existing = contacts.findByEmailIgnoreCase(email);
      OutreachContact c = existing.orElseGet(() -> OutreachContact.builder().email(email).build());
      if (existing.isPresent() && PROTECTED.contains(c.getStatus())) {
        protectedCount++;
        continue;
      }
      c.setName(name);
      c.setSector(blankTo(r.get("sector"), "Other"));
      c.setPhone(trimOrNull(r.get("phone")));
      c.setWebsite(trimOrNull(r.get("website")));
      c.setCity(trimOrNull(r.get("city")));
      c.setConfidence(trimOrNull(r.get("confidence")));
      c.setSourceUrl(trimOrNull(r.get("source_url")));
      c.setNotes(trimOrNull(r.get("notes")));
      if (trimOrNull(r.get("locale")) != null) c.setLocale(MailLocale.normalize(r.get("locale")));
      if (trimOrNull(r.get("hook")) != null) c.setHook(r.get("hook").trim());
      if (trimOrNull(r.get("contact_name")) != null) c.setContactName(r.get("contact_name").trim());
      if (trimOrNull(r.get("owner")) != null) c.setOwner(r.get("owner").trim());
      if (trimOrNull(r.get("template_key")) != null) {
        c.setTemplateKey(r.get("template_key").trim().toUpperCase(Locale.ROOT));
      }
      contacts.save(c);
      if (existing.isPresent()) updated++;
      else added++;
    }
    return ImportResult.builder()
        .added(added)
        .updated(updated)
        .skippedInvalid(invalid)
        .skippedProtected(protectedCount)
        .problems(problems)
        .build();
  }

  // ------------------------------------------------------------------ contacts

  @Transactional(readOnly = true)
  public Page<ContactResponse> search(
      OutreachContact.Status status, String sector, String q, Pageable pageable) {
    return contacts
        .search(status, blankToNull(sector), blankToNull(q), pageable)
        .map(this::toResponse);
  }

  @Transactional(readOnly = true)
  public ContactResponse get(UUID id) {
    return toResponse(load(id));
  }

  @Transactional
  public ContactResponse update(UUID id, UpdateContactRequest r) {
    OutreachContact c = load(id);
    if (r.getContactName() != null) c.setContactName(trimOrNull(r.getContactName()));
    if (r.getTemplateKey() != null) {
      String k = r.getTemplateKey().trim().toUpperCase(Locale.ROOT);
      if (!k.isEmpty()) TemplateKey.valueOf(k);
      c.setTemplateKey(k.isEmpty() ? null : k);
    }
    if (r.getLocale() != null) c.setLocale(MailLocale.normalize(r.getLocale()));
    if (r.getHook() != null) c.setHook(trimOrNull(r.getHook()));
    if (r.getOwner() != null) c.setOwner(trimOrNull(r.getOwner()));
    if (r.getNotes() != null) c.setNotes(trimOrNull(r.getNotes()));
    if (r.getPhone() != null) c.setPhone(trimOrNull(r.getPhone()));
    if (r.getStatus() != null) setStatus(c, r.getStatus());
    return toResponse(contacts.save(c));
  }

  /** Queue the first email. Only NEW or PAUSED contacts move; others are left as they are. */
  @Transactional
  public int queue(List<UUID> ids) {
    int n = 0;
    LocalDateTime now = LocalDateTime.now();
    for (UUID id : ids) {
      OutreachContact c = load(id);
      if (c.getStatus() == OutreachContact.Status.NEW
          || c.getStatus() == OutreachContact.Status.PAUSED) {
        if (c.getStep() == 0) {
          c.setStatus(OutreachContact.Status.QUEUED);
        } else {
          c.setStatus(OutreachContact.Status.SENT); // resume waiting for the follow-up
        }
        if (c.getNextActionAt() == null || c.getNextActionAt().isBefore(now)) {
          c.setNextActionAt(now.plusSeconds(n * 30L));
        }
        c.setLastError(null);
        contacts.save(c);
        n++;
      }
    }
    return n;
  }

  @Transactional
  public int pause(List<UUID> ids) {
    int n = 0;
    for (UUID id : ids) {
      OutreachContact c = load(id);
      if (c.getStatus() == OutreachContact.Status.QUEUED
          || c.getStatus() == OutreachContact.Status.SENT) {
        c.setStatus(OutreachContact.Status.PAUSED);
        contacts.save(c);
        n++;
      }
    }
    return n;
  }

  private void setStatus(OutreachContact c, OutreachContact.Status s) {
    c.setStatus(s);
    if (s == OutreachContact.Status.REPLIED && c.getRepliedAt() == null) {
      c.setRepliedAt(LocalDateTime.now());
    }
    if (s == OutreachContact.Status.QUEUED && c.getNextActionAt() == null) {
      c.setNextActionAt(LocalDateTime.now());
    }
  }

  // ------------------------------------------------------------------ preview / messages

  @Transactional(readOnly = true)
  public PreviewResponse preview(UUID id, int step) {
    OutreachContact c = load(id);
    OutreachTemplateRenderer.Rendered r = render(c, step);
    boolean first = step != 2;
    OutreachMailer.Options o =
        first ? OutreachMailer.Options.firstEmail() : OutreachMailer.Options.followUp();
    return PreviewResponse.builder()
        .to(c.getEmail())
        .subject(r.subject())
        .body(r.body())
        .html(
            mailer.html(
                r.subject(), r.body(), c.getLocale(), o.includePackages(), renderer.registerUrl(c)))
        .templateKey(
            (first ? OutreachTemplateRenderer.effectiveKey(c) : TemplateKey.FOLLOW_UP).name())
        .locale(c.getLocale())
        .attachments(
            OutreachMailer.attachmentNames(c.getLocale(), o).stream()
                .map(OutreachMailer::displayName)
                .toList())
        .build();
  }

  /** HTML for text the admin edited in the drawer. */
  @Transactional(readOnly = true)
  public String renderHtml(UUID id, RenderRequest r) {
    OutreachContact c = load(id);
    return mailer.html(
        r.getSubject() == null ? "" : r.getSubject(),
        r.getBody() == null ? "" : r.getBody(),
        c.getLocale(),
        r.isIncludePackages(),
        renderer.registerUrl(c));
  }

  public OutreachTemplateRenderer.Rendered render(OutreachContact c, int step) {
    TemplateKey key = step == 2 ? TemplateKey.FOLLOW_UP : OutreachTemplateRenderer.effectiveKey(c);
    String locale = MailLocale.normalize(c.getLocale());
    OutreachTemplate t =
        templates
            .findByTemplateKeyAndLocale(key.name(), locale)
            .or(() -> templates.findByTemplateKeyAndLocale(key.name(), MailLocale.EN))
            .orElseThrow(() -> new BusinessException("No template for " + key + " / " + locale));
    String firstSubject =
        messages.findByContactIdOrderBySentAtAsc(c.getId()).stream()
            .findFirst()
            .map(OutreachMessage::getSubject)
            .orElse(null);
    return renderer.render(t, c, firstSubject);
  }

  @Transactional(readOnly = true)
  public List<MessageResponse> messagesFor(UUID id) {
    return messages.findByContactIdOrderBySentAtAsc(id).stream()
        .map(
            m ->
                MessageResponse.builder()
                    .id(m.getId())
                    .step(m.getStep())
                    .subject(m.getSubject())
                    .body(m.getBody())
                    .sentAt(m.getSentAt())
                    .build())
        .toList();
  }

  /** Sends the rendered email for a contact to an address of the admin's choosing. */
  @Transactional(readOnly = true)
  public void sendTest(UUID id, String to, int step) {
    if (!mailer.configured()) throw new BusinessException("Outgoing mail is not configured");
    if (to == null || !EMAIL.matcher(to.trim()).matches()) {
      throw new BusinessException("A valid test address is required");
    }
    OutreachContact c = load(id);
    OutreachTemplateRenderer.Rendered r = render(c, step);
    try {
      mailer.send(
          to.trim(),
          "[TEST] " + r.subject(),
          r.body(),
          c.getLocale(),
          renderer.registerUrl(c),
          null,
          step == 2 ? OutreachMailer.Options.followUp() : OutreachMailer.Options.firstEmail());
    } catch (Exception e) {
      throw new BusinessException("Test send failed: " + e.getMessage());
    }
  }

  /**
   * Admin-triggered send with the content as edited in the drawer. Ignores the scheduler's window
   * and caps (a person decided), refuses contacts who opted out or bounced, and records the message
   * so the follow-up threads onto it.
   */
  @Transactional
  public ContactResponse sendNow(UUID id, SendNowRequest r) {
    if (!mailer.configured()) throw new BusinessException("Outgoing mail is not configured");
    OutreachContact c = load(id);
    if (c.getStatus() == OutreachContact.Status.OPTED_OUT
        || c.getStatus() == OutreachContact.Status.BOUNCED) {
      throw new BusinessException("This contact opted out or bounced; nothing is sent.");
    }
    if (r.getSubject() == null
        || r.getSubject().isBlank()
        || r.getBody() == null
        || r.getBody().isBlank()) {
      throw new BusinessException("Subject and body are required");
    }
    boolean first = c.getStep() == 0;
    String inReplyTo =
        first
            ? null
            : messages.findByContactIdOrderBySentAtAsc(c.getId()).stream()
                .map(OutreachMessage::getProviderMessageId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    String messageId;
    try {
      messageId =
          mailer.send(
              c.getEmail(),
              r.getSubject().trim(),
              r.getBody(),
              c.getLocale(),
              renderer.registerUrl(c),
              inReplyTo,
              new OutreachMailer.Options(
                  r.isIncludePackages(),
                  r.isAttachBrochure(),
                  r.isAttachSponsorship(),
                  r.isAttachAgreement()));
    } catch (Exception e) {
      c.setLastError(e.getMessage() == null ? "send failed" : e.getMessage());
      contacts.save(c);
      throw new BusinessException("Send failed: " + e.getMessage());
    }
    LocalDateTime now = LocalDateTime.now();
    int step = first ? 1 : Math.max(2, c.getStep() + 1);
    messages.save(
        OutreachMessage.builder()
            .contact(c)
            .step(step)
            .subject(r.getSubject().trim())
            .body(r.getBody())
            .providerMessageId(messageId)
            .sentAt(now)
            .build());
    c.setStep(step);
    c.setLastSentAt(now);
    c.setLastError(null);
    if (first && r.isScheduleFollowUp()) {
      c.setStatus(OutreachContact.Status.SENT);
      c.setNextActionAt(OutreachScheduler.addBusinessDays(now, followUpAfterDays()));
    } else if (c.getStatus() == OutreachContact.Status.NEW
        || c.getStatus() == OutreachContact.Status.QUEUED
        || c.getStatus() == OutreachContact.Status.SENT
        || c.getStatus() == OutreachContact.Status.PAUSED) {
      c.setStatus(OutreachContact.Status.FOLLOWED_UP);
      c.setNextActionAt(null);
    }
    return toResponse(contacts.save(c));
  }

  private int followUpAfterDays() {
    return renderer.props().getFollowUpAfterDays();
  }

  // ------------------------------------------------------------------ templates

  @Transactional
  public List<TemplateResponse> listTemplates() {
    seedTemplatesIfEmpty();
    return templates.findAllByOrderByTemplateKeyAscLocaleAsc().stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public TemplateResponse updateTemplate(UUID id, TemplateRequest r) {
    OutreachTemplate t =
        templates
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("OutreachTemplate", id));
    if (r.getSubject() == null
        || r.getSubject().isBlank()
        || r.getBody() == null
        || r.getBody().isBlank()) {
      throw new BusinessException("Subject and body are required");
    }
    t.setSubject(r.getSubject().trim());
    t.setBody(r.getBody().trim());
    return toResponse(templates.save(t));
  }

  @Transactional
  public void seedTemplatesIfEmpty() {
    if (templates.count() > 0) return;
    try {
      Resource[] files =
          new PathMatchingResourcePatternResolver().getResources("classpath:outreach/*.txt");
      for (Resource f : files) {
        String name = Objects.requireNonNull(f.getFilename());
        String[] parts = name.replace(".txt", "").split("\\.");
        String text = new String(f.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String[] lines = text.split("\n", 2);
        String subject = lines[0].replaceFirst("^Subject:\\s*", "").trim();
        String body = lines.length > 1 ? lines[1].trim() : "";
        templates.save(
            OutreachTemplate.builder()
                .templateKey(parts[0])
                .locale(parts[1])
                .subject(subject)
                .body(body)
                .build());
      }
      log.info("Seeded {} outreach templates", files.length);
    } catch (IOException e) {
      log.error("Could not seed outreach templates: {}", e.getMessage());
    }
  }

  // ------------------------------------------------------------------ helpers

  OutreachContact load(UUID id) {
    return contacts
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("OutreachContact", id));
  }

  ContactResponse toResponse(OutreachContact c) {
    return ContactResponse.builder()
        .id(c.getId())
        .name(c.getName())
        .contactName(c.getContactName())
        .sector(c.getSector())
        .templateKey(c.getTemplateKey())
        .effectiveTemplateKey(OutreachTemplateRenderer.effectiveKey(c).name())
        .email(c.getEmail())
        .phone(c.getPhone())
        .website(c.getWebsite())
        .city(c.getCity())
        .locale(c.getLocale())
        .confidence(c.getConfidence())
        .sourceUrl(c.getSourceUrl())
        .notes(c.getNotes())
        .hook(c.getHook())
        .owner(c.getOwner())
        .status(c.getStatus())
        .step(c.getStep())
        .nextActionAt(c.getNextActionAt())
        .lastSentAt(c.getLastSentAt())
        .repliedAt(c.getRepliedAt())
        .threadId(c.getThreadId())
        .lastError(c.getLastError())
        .createdAt(c.getCreatedAt())
        .updatedAt(c.getUpdatedAt())
        .build();
  }

  private TemplateResponse toResponse(OutreachTemplate t) {
    return TemplateResponse.builder()
        .id(t.getId())
        .templateKey(t.getTemplateKey())
        .locale(t.getLocale())
        .subject(t.getSubject())
        .body(t.getBody())
        .build();
  }

  /** RFC 4180-ish CSV parser: quoted fields, doubled quotes, newlines inside quotes. */
  static List<Map<String, String>> parseCsv(Reader reader) throws IOException {
    BufferedReader br = new BufferedReader(reader);
    String text = br.lines().reduce((a, b) -> a + "\n" + b).orElse("");
    if (text.startsWith("﻿")) text = text.substring(1);
    List<List<String>> records = new ArrayList<>();
    List<String> cur = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (quoted) {
        if (ch == '"') {
          if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
            field.append('"');
            i++;
          } else {
            quoted = false;
          }
        } else {
          field.append(ch);
        }
      } else if (ch == '"') {
        quoted = true;
      } else if (ch == ',') {
        cur.add(field.toString());
        field.setLength(0);
      } else if (ch == '\n' || ch == '\r') {
        if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
        cur.add(field.toString());
        field.setLength(0);
        records.add(cur);
        cur = new ArrayList<>();
      } else {
        field.append(ch);
      }
    }
    if (field.length() > 0 || !cur.isEmpty()) {
      cur.add(field.toString());
      records.add(cur);
    }
    if (records.isEmpty()) return List.of();
    List<String> header =
        records.get(0).stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
    List<Map<String, String>> rows = new ArrayList<>();
    for (List<String> rec : records.subList(1, records.size())) {
      if (rec.stream().allMatch(String::isBlank)) continue;
      Map<String, String> m = new HashMap<>();
      for (int i = 0; i < header.size() && i < rec.size(); i++) m.put(header.get(i), rec.get(i));
      rows.add(m);
    }
    return rows;
  }

  private static String trimOrNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  private static String blankTo(String s, String d) {
    return s == null || s.isBlank() ? d : s.trim();
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
