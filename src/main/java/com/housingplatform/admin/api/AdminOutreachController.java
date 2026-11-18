package com.housingplatform.admin.api;

import com.housingplatform.outreach.domain.OutreachContact;
import com.housingplatform.outreach.dto.OutreachDtos.*;
import com.housingplatform.outreach.service.OutreachScheduler;
import com.housingplatform.outreach.service.OutreachService;
import com.housingplatform.outreach.service.OutreachSettingsService;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** B2B outreach sequencer: curated contacts, per-sector templates, capped one-to-one sending. */
@RestController
@RequestMapping("/api/v1/admin/outreach")
@Tag(name = "Admin - Outreach", description = "Partner and exhibitor outreach")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
public class AdminOutreachController {

  private final OutreachService service;
  private final OutreachScheduler scheduler;
  private final OutreachSettingsService settings;

  @GetMapping("/stats")
  public ResponseEntity<Stats> stats() {
    return ResponseEntity.ok(scheduler.stats());
  }

  @PutMapping("/settings")
  @Operation(summary = "Switch the scheduler on or off and set caps and window")
  public ResponseEntity<Stats> updateSettings(@RequestBody SettingsRequest body) {
    settings.update(body);
    return ResponseEntity.ok(scheduler.stats());
  }

  @PostMapping(value = "/contacts/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Import or update contacts from the prospect-list CSV")
  public ResponseEntity<ImportResult> importCsv(@RequestParam("file") MultipartFile file)
      throws IOException {
    return ResponseEntity.ok(service.importCsv(file.getInputStream()));
  }

  @GetMapping("/contacts")
  public ResponseEntity<Page<ContactResponse>> list(
      @RequestParam(required = false) OutreachContact.Status status,
      @RequestParam(required = false) String sector,
      @RequestParam(required = false) String q,
      @PageableDefault(size = 50) Pageable pageable) {
    return ResponseEntity.ok(service.search(status, sector, q, pageable));
  }

  @GetMapping("/contacts/{id}")
  public ResponseEntity<ContactResponse> get(@PathVariable UUID id) {
    return ResponseEntity.ok(service.get(id));
  }

  @PatchMapping("/contacts/{id}")
  public ResponseEntity<ContactResponse> update(
      @PathVariable UUID id, @RequestBody UpdateContactRequest body) {
    return ResponseEntity.ok(service.update(id, body));
  }

  @PostMapping("/contacts/queue")
  @Operation(summary = "Queue the first email for the given contacts")
  public ResponseEntity<Map<String, Integer>> queue(@RequestBody IdsRequest body) {
    return ResponseEntity.ok(Map.of("queued", service.queue(body.getIds())));
  }

  @PostMapping("/contacts/pause")
  public ResponseEntity<Map<String, Integer>> pause(@RequestBody IdsRequest body) {
    return ResponseEntity.ok(Map.of("paused", service.pause(body.getIds())));
  }

  @GetMapping("/contacts/{id}/preview")
  public ResponseEntity<PreviewResponse> preview(
      @PathVariable UUID id, @RequestParam(defaultValue = "1") int step) {
    return ResponseEntity.ok(service.preview(id, step));
  }

  @PostMapping(value = "/contacts/{id}/render", produces = MediaType.TEXT_HTML_VALUE)
  @Operation(summary = "Render the branded HTML for edited text (live preview)")
  public ResponseEntity<String> render(@PathVariable UUID id, @RequestBody RenderRequest body) {
    return ResponseEntity.ok(service.renderHtml(id, body));
  }

  @GetMapping("/contacts/{id}/messages")
  public ResponseEntity<List<MessageResponse>> messagesFor(@PathVariable UUID id) {
    return ResponseEntity.ok(service.messagesFor(id));
  }

  @PostMapping("/contacts/{id}/send-test")
  @Operation(summary = "Send the rendered email for this contact to a test address")
  public ResponseEntity<Void> sendTest(@PathVariable UUID id, @RequestBody SendTestRequest body) {
    service.sendTest(id, body.getTo(), body.getStep());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/contacts/{id}/send")
  @Operation(summary = "Send the composed email to this contact now (admin-triggered)")
  public ResponseEntity<ContactResponse> sendNow(
      @PathVariable UUID id, @RequestBody SendNowRequest body) {
    return ResponseEntity.ok(service.sendNow(id, body));
  }

  @GetMapping("/templates")
  public ResponseEntity<List<TemplateResponse>> templates() {
    return ResponseEntity.ok(service.listTemplates());
  }

  @PutMapping("/templates/{id}")
  public ResponseEntity<TemplateResponse> updateTemplate(
      @PathVariable UUID id, @RequestBody TemplateRequest body) {
    return ResponseEntity.ok(service.updateTemplate(id, body));
  }

  @PostMapping("/sync")
  @Operation(summary = "Read the reply-to mailbox now for replies and opt-outs")
  public ResponseEntity<Void> sync() {
    scheduler.syncReplies();
    return ResponseEntity.noContent().build();
  }
}
