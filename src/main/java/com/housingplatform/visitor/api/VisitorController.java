package com.housingplatform.visitor.api;

import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import com.housingplatform.visitor.dto.VisitorDtos.Badge;
import com.housingplatform.visitor.dto.VisitorDtos.RegisterRequest;
import com.housingplatform.visitor.service.VisitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/visitors")
@Tag(name = "Visitors", description = "Expo visitor pre-registration and badges (public)")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
public class VisitorController {

  private final VisitorService service;

  @PostMapping("/register")
  @Operation(
      summary = "Register to visit the expo",
      description =
          "Public, no account needed. Creates (or refreshes) the badge for this email and emails the QR code.")
  public ResponseEntity<Badge> register(@Valid @RequestBody RegisterRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.register(request));
  }

  @GetMapping("/{code}")
  @Operation(summary = "The badge behind a code (name, type, status)")
  public ResponseEntity<Badge> badge(@PathVariable String code) {
    return ResponseEntity.ok(service.badge(code));
  }

  @GetMapping(value = "/{code}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
  @Operation(summary = "The badge's QR code as PNG")
  public ResponseEntity<byte[]> qr(
      @PathVariable String code, @RequestParam(defaultValue = "440") int size) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
        .contentType(MediaType.IMAGE_PNG)
        .body(service.qrPng(code, size));
  }
}
