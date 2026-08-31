package com.housingplatform.download.api;

import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public "get the app" endpoints for both platforms.
 *
 * <ul>
 *   <li>{@code GET /api/v1/public/download} — redirects by device: iOS → TestFlight, everything
 *       else → the Android APK.
 *   <li>{@code GET /api/v1/public/download/android} — streams the APK from disk.
 *   <li>{@code GET /api/v1/public/download/ios} — redirects to the configured TestFlight/App Store
 *       URL (or reports it isn't available yet).
 *   <li>{@code GET /api/v1/public/download/info} — JSON describing what's available (for the
 *       install page / nav).
 * </ul>
 *
 * <p>Drop the built APK at {@code ${app.downloads.dir}/${app.downloads.android-file}} on the
 * server; no binaries live in the repo.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/public/download")
@Tag(name = "Downloads", description = "Public app downloads (Android APK + iOS TestFlight)")
public class DownloadController {

  private static final String APK_MEDIA_TYPE = "application/vnd.android.package-archive";

  @Value("${app.downloads.dir:./downloads}")
  private String dir;

  @Value("${app.downloads.android-file:ethio-build-connect.apk}")
  private String androidFile;

  @Value("${app.downloads.android-version:}")
  private String androidVersion;

  @Value("${app.downloads.ios-url:}")
  private String iosUrl;

  @GetMapping
  @AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
  @Operation(summary = "Get the app", description = "Redirects to the right store/file for the device.")
  public ResponseEntity<Void> get(
      @RequestHeader(value = "User-Agent", required = false) String userAgent) {
    String ua = userAgent == null ? "" : userAgent.toLowerCase();
    boolean ios = ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod");
    String target =
        ios ? "/api/v1/public/download/ios" : "/api/v1/public/download/android";
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
  }

  @GetMapping("/android")
  @AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
  @Operation(summary = "Download the Android APK", description = "Streams the APK for sideloading.")
  public ResponseEntity<Resource> android() {
    Path file = apkPath();
    if (file == null || !Files.isReadable(file)) {
      return ResponseEntity.notFound().build();
    }
    long length;
    try {
      length = Files.size(file);
    } catch (IOException e) {
      log.error("Failed to stat APK at {}", file, e);
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(APK_MEDIA_TYPE))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + androidFile + "\"")
        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
        .contentLength(length)
        .body(new PathResource(file));
  }

  @GetMapping("/ios")
  @AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
  @Operation(summary = "Open the iOS app", description = "Redirects to TestFlight / the App Store.")
  public ResponseEntity<?> ios() {
    if (iosUrl == null || iosUrl.isBlank()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("message", "The iOS app isn't available yet — it's coming via TestFlight."));
    }
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(iosUrl)).build();
  }

  @GetMapping("/info")
  @AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
  @Operation(summary = "Availability of each app", description = "What the install page can offer.")
  public ResponseEntity<Map<String, Object>> info() {
    Path file = apkPath();
    boolean androidReady = file != null && Files.isReadable(file);

    Map<String, Object> android = new LinkedHashMap<>();
    android.put("available", androidReady);
    android.put("url", "/api/v1/public/download/android");
    if (androidVersion != null && !androidVersion.isBlank()) {
      android.put("version", androidVersion);
    }
    if (androidReady) {
      try {
        android.put("sizeBytes", Files.size(file));
      } catch (IOException ignored) {
        // size is best-effort
      }
    }

    Map<String, Object> ios = new LinkedHashMap<>();
    boolean iosReady = iosUrl != null && !iosUrl.isBlank();
    ios.put("available", iosReady);
    ios.put("url", iosReady ? iosUrl : null);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("android", android);
    body.put("ios", ios);
    return ResponseEntity.ok(body);
  }

  private Path apkPath() {
    if (dir == null || dir.isBlank() || androidFile == null || androidFile.isBlank()) {
      return null;
    }
    // Guard against path traversal in the configured filename.
    if (androidFile.contains("..") || androidFile.contains("/") || androidFile.contains("\\")) {
      return null;
    }
    return Path.of(dir).resolve(androidFile).normalize();
  }
}
