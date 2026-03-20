package com.housingplatform.media.api;

import com.housingplatform.media.service.MediaStorageService;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves files from the upload directory. URLs are stored in the DB (e.g.
 * /api/v1/uploads/properties/id/file.jpg).
 *
 * <p>Returns a {@link Resource} so Spring can stream the body and honor {@code Range} requests
 * (required for HTML5 video seeking and progressive playback). The previous {@code readAllBytes()}
 * approach buffered entire files and did not support byte ranges, which breaks or slows video in
 * browsers.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadsController {

  private final MediaStorageService mediaStorageService;

  public UploadsController(MediaStorageService mediaStorageService) {
    this.mediaStorageService = mediaStorageService;
  }

  @GetMapping("/**")
  public ResponseEntity<Resource> serveFile(jakarta.servlet.http.HttpServletRequest request) {
    String prefix = "/api/v1/uploads";
    String uri = request.getRequestURI();
    if (uri == null || !uri.startsWith(prefix) || uri.length() <= prefix.length()) {
      return ResponseEntity.notFound().build();
    }
    String path = uri.substring(prefix.length());
    if (path.startsWith("/")) {
      path = path.substring(1);
    }
    String imageUrl = prefix + "/" + path;
    if (!mediaStorageService.isUploadsUrl(imageUrl)) {
      return ResponseEntity.notFound().build();
    }
    try {
      Path file = mediaStorageService.resolveUploadPath(imageUrl);
      String filename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
      String contentType = guessContentType(filename);
      Resource resource = new PathResource(file);
      return ResponseEntity.ok()
          .contentType(MediaType.parseMediaType(contentType))
          .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
          .header(HttpHeaders.ACCEPT_RANGES, "bytes")
          .lastModified(Files.getLastModifiedTime(file).toMillis())
          .body(resource);
    } catch (FileNotFoundException e) {
      return ResponseEntity.notFound().build();
    } catch (IOException e) {
      log.error("Failed to serve upload path={} url={}", path, imageUrl, e);
      throw new UncheckedIOException(e);
    }
  }

  private static String guessContentType(String filename) {
    String lower = filename.toLowerCase();
    if (lower.endsWith(".png")) return "image/png";
    if (lower.endsWith(".gif")) return "image/gif";
    if (lower.endsWith(".webp")) return "image/webp";
    if (lower.endsWith(".mp4")) return "video/mp4";
    if (lower.endsWith(".webm")) return "video/webm";
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
    if (lower.endsWith(".pdf")) return "application/pdf";
    if (lower.endsWith(".doc")) return "application/msword";
    if (lower.endsWith(".docx"))
      return "application/vnd.openxmlformats-officedocument.wordprocessorml.document";
    return "application/octet-stream";
  }
}
