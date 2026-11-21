package com.housingplatform.visitor.service;

import com.housingplatform.shared.exception.BusinessException;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import com.housingplatform.shared.mail.MailLocale;
import com.housingplatform.visitor.domain.VisitorRegistration;
import com.housingplatform.visitor.domain.VisitorRegistration.Status;
import com.housingplatform.visitor.domain.VisitorRegistration.VisitorType;
import com.housingplatform.visitor.dto.VisitorDtos.AdminRow;
import com.housingplatform.visitor.dto.VisitorDtos.Badge;
import com.housingplatform.visitor.dto.VisitorDtos.DayCount;
import com.housingplatform.visitor.dto.VisitorDtos.RegisterRequest;
import com.housingplatform.visitor.dto.VisitorDtos.Stats;
import com.housingplatform.visitor.repository.VisitorRegistrationRepository;
import com.housingplatform.visitor.util.QrCodes;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class VisitorService {

  /** No 0/O/1/I so a code read out at the door is unambiguous. */
  private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

  private static final Pattern CODE =
      Pattern.compile("(EBC-[A-Z0-9]{6})", Pattern.CASE_INSENSITIVE);
  private static final SecureRandom RANDOM = new SecureRandom();

  private final VisitorRegistrationRepository repository;
  private final VisitorBadgeMailService mail;

  @Transactional
  public Badge register(RegisterRequest r) {
    if (r.getWebsite() != null && !r.getWebsite().isBlank()) {
      log.info("Visitor registration honeypot tripped for {}", r.getEmail());
      return Badge.builder()
          .code("EBC-XXXXXX")
          .fullName(r.getFullName())
          .status("REGISTERED")
          .build();
    }
    String email = r.getEmail().trim();
    VisitorRegistration existing = repository.findByEmailIgnoreCase(email).orElse(null);
    if (existing != null) {
      // Same person again: refresh the details they typed and resend the badge, keep the code.
      if (existing.getStatus() == Status.CANCELLED) existing.setStatus(Status.REGISTERED);
      existing.setFullName(r.getFullName().trim());
      existing.setPhone(blankToNull(r.getPhone()));
      existing.setOrganization(blankToNull(r.getOrganization()));
      existing.setVisitorType(type(r.getVisitorType()));
      existing.setInterests(blankToNull(r.getInterests()));
      existing.setLocale(MailLocale.normalize(r.getLocale()));
      VisitorRegistration saved = repository.save(existing);
      afterCommit(() -> mail.sendBadge(saved));
      return toBadge(saved, true);
    }
    VisitorRegistration v =
        VisitorRegistration.builder()
            .code(newCode())
            .fullName(r.getFullName().trim())
            .email(email)
            .phone(blankToNull(r.getPhone()))
            .organization(blankToNull(r.getOrganization()))
            .visitorType(type(r.getVisitorType()))
            .interests(blankToNull(r.getInterests()))
            .locale(MailLocale.normalize(r.getLocale()))
            .status(Status.REGISTERED)
            .source(blankToNull(r.getSource()) == null ? "web" : r.getSource().trim())
            .build();
    VisitorRegistration saved = repository.save(v);
    afterCommit(() -> mail.sendBadge(saved));
    return toBadge(saved, false);
  }

  @Transactional(readOnly = true)
  public Badge badge(String code) {
    return toBadge(load(code), false);
  }

  @Transactional(readOnly = true)
  public byte[] qrPng(String code, int size) {
    VisitorRegistration v = load(code);
    return QrCodes.png(mail.badgeUrl(v), Math.max(160, Math.min(size, 1024)));
  }

  // ---------------------------------------------------------------- admin

  @Transactional(readOnly = true)
  public Page<AdminRow> search(String q, Status status, VisitorType type, Pageable pageable) {
    Specification<VisitorRegistration> spec = (root, query, cb) -> cb.conjunction();
    if (status != null) spec = spec.and((r, qq, cb) -> cb.equal(r.get("status"), status));
    if (type != null) spec = spec.and((r, qq, cb) -> cb.equal(r.get("visitorType"), type));
    if (q != null && !q.isBlank()) {
      String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
      spec =
          spec.and(
              (r, qq, cb) ->
                  cb.or(
                      cb.like(cb.lower(r.get("fullName")), like),
                      cb.like(cb.lower(r.get("email")), like),
                      cb.like(cb.lower(r.get("code")), like),
                      cb.like(cb.lower(cb.coalesce(r.get("organization"), "")), like),
                      cb.like(cb.lower(cb.coalesce(r.get("phone"), "")), like)));
    }
    Pageable sorted =
        pageable.getSort().isSorted()
            ? pageable
            : PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    return repository.findAll(spec, sorted).map(VisitorService::toRow);
  }

  @Transactional(readOnly = true)
  public Stats stats() {
    LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
    LocalDateTime weekAgo = startOfDay.minusDays(6);
    Map<String, Long> byType = new LinkedHashMap<>();
    for (VisitorType t : VisitorType.values()) byType.put(t.name(), 0L);
    for (Object[] row : repository.countsByType()) {
      byType.put(((VisitorType) row[0]).name(), ((Number) row[1]).longValue());
    }
    List<DayCount> byDay = new ArrayList<>();
    Map<String, Long> days = new LinkedHashMap<>();
    for (int i = 6; i >= 0; i--) days.put(LocalDate.now().minusDays(i).toString(), 0L);
    for (Object[] row : repository.countsByDay(weekAgo)) {
      days.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
    }
    days.forEach((d, c) -> byDay.add(new DayCount(d, c)));
    long cancelled = repository.countByStatus(Status.CANCELLED);
    return Stats.builder()
        .total(repository.count() - cancelled)
        .checkedIn(repository.countByStatus(Status.CHECKED_IN))
        .today(repository.countByCreatedAtGreaterThanEqual(startOfDay))
        .checkedInToday(repository.countByCheckedInAtGreaterThanEqual(startOfDay))
        .last7Days(repository.countByCreatedAtGreaterThanEqual(weekAgo))
        .byType(byType)
        .byDay(byDay)
        .build();
  }

  /** Marks a badge as used at the door. Accepts the code or the full badge URL a camera reads. */
  @Transactional
  public AdminRow checkIn(String raw, String by) {
    VisitorRegistration v = load(extractCode(raw));
    if (v.getStatus() == Status.CANCELLED) throw new BusinessException("This badge was cancelled");
    if (v.getStatus() == Status.CHECKED_IN) {
      throw new BusinessException(
          "Already checked in at " + v.getCheckedInAt().withNano(0) + " (" + v.getFullName() + ")");
    }
    v.setStatus(Status.CHECKED_IN);
    v.setCheckedInAt(LocalDateTime.now());
    v.setCheckedInBy(by);
    return toRow(repository.save(v));
  }

  @Transactional
  public AdminRow setStatus(UUID id, Status status) {
    VisitorRegistration v =
        repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Visitor", id));
    v.setStatus(status);
    if (status != Status.CHECKED_IN) {
      v.setCheckedInAt(null);
      v.setCheckedInBy(null);
    } else if (v.getCheckedInAt() == null) {
      v.setCheckedInAt(LocalDateTime.now());
    }
    return toRow(repository.save(v));
  }

  @Transactional(readOnly = true)
  public void resend(UUID id) {
    VisitorRegistration v =
        repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Visitor", id));
    mail.sendBadge(v);
  }

  @Transactional(readOnly = true)
  public String exportCsv() {
    StringBuilder sb = new StringBuilder();
    sb.append(
        "code,full_name,email,phone,organization,visitor_type,interests,locale,status,checked_in_at,registered_at\n");
    for (VisitorRegistration v : repository.findAll(Sort.by(Sort.Direction.ASC, "createdAt"))) {
      sb.append(
              String.join(
                  ",",
                  csv(v.getCode()),
                  csv(v.getFullName()),
                  csv(v.getEmail()),
                  csv(v.getPhone()),
                  csv(v.getOrganization()),
                  csv(v.getVisitorType().name()),
                  csv(v.getInterests()),
                  csv(v.getLocale()),
                  csv(v.getStatus().name()),
                  csv(v.getCheckedInAt() == null ? "" : v.getCheckedInAt().withNano(0).toString()),
                  csv(v.getCreatedAt() == null ? "" : v.getCreatedAt().withNano(0).toString())))
          .append('\n');
    }
    return sb.toString();
  }

  // ---------------------------------------------------------------- helpers

  public static String extractCode(String raw) {
    if (raw == null) throw new BusinessException("Badge code is required");
    Matcher m = CODE.matcher(raw.trim());
    if (m.find()) return m.group(1).toUpperCase(Locale.ROOT);
    String bare = raw.trim().toUpperCase(Locale.ROOT).replace(" ", "");
    if (bare.matches("[A-Z0-9]{6}")) return "EBC-" + bare;
    throw new BusinessException("That does not look like a badge code");
  }

  private VisitorRegistration load(String code) {
    String c = extractCode(code);
    return repository
        .findByCodeIgnoreCase(c)
        .orElseThrow(() -> new ResourceNotFoundException("Badge", c));
  }

  private String newCode() {
    for (int attempt = 0; attempt < 20; attempt++) {
      StringBuilder sb = new StringBuilder("EBC-");
      for (int i = 0; i < 6; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
      String code = sb.toString();
      if (!repository.existsByCodeIgnoreCase(code)) return code;
    }
    throw new IllegalStateException("Could not allocate a badge code");
  }

  static VisitorType type(String raw) {
    if (raw == null || raw.isBlank()) return VisitorType.OTHER;
    try {
      return VisitorType.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    } catch (IllegalArgumentException e) {
      return VisitorType.OTHER;
    }
  }

  private Badge toBadge(VisitorRegistration v, boolean already) {
    String url = mail.badgeUrl(v);
    return Badge.builder()
        .code(v.getCode())
        .fullName(v.getFullName())
        .organization(v.getOrganization())
        .visitorType(v.getVisitorType().name())
        .status(v.getStatus().name())
        .locale(v.getLocale())
        .checkedInAt(v.getCheckedInAt())
        .createdAt(v.getCreatedAt())
        .badgeUrl(url)
        .qrUrl("/api/v1/visitors/" + v.getCode() + "/qr")
        .alreadyRegistered(already)
        .build();
  }

  static AdminRow toRow(VisitorRegistration v) {
    return AdminRow.builder()
        .id(v.getId())
        .code(v.getCode())
        .fullName(v.getFullName())
        .email(v.getEmail())
        .phone(v.getPhone())
        .organization(v.getOrganization())
        .visitorType(v.getVisitorType().name())
        .interests(v.getInterests())
        .locale(v.getLocale())
        .status(v.getStatus().name())
        .source(v.getSource())
        .checkedInAt(v.getCheckedInAt())
        .checkedInBy(v.getCheckedInBy())
        .badgeSentAt(v.getBadgeSentAt())
        .createdAt(v.getCreatedAt())
        .build();
  }

  private static void afterCommit(Runnable r) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              r.run();
            }
          });
    } else {
      r.run();
    }
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  private static String csv(String s) {
    if (s == null) return "";
    String v = s.replace("\"", "\"\"");
    return (v.contains(",") || v.contains("\"") || v.contains("\n")) ? "\"" + v + "\"" : v;
  }
}
