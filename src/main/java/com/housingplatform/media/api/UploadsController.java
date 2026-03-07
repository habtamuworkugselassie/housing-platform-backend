package com.housingplatform.media.api;

import com.housingplatform.media.service.MediaStorageService;
import java.io.InputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves files from the upload directory. URLs are stored in the DB (e.g.
 * /api/v1/uploads/properties/id/file.jpg).
 */
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadsController {

  private final MediaStorageService mediaStorageService;

  public UploadsController(MediaStorageService mediaStorageService) {
    this.mediaStorageService = mediaStorageService;
  }

  @GetMapping(value = "/**", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  public ResponseEntity<byte[]> serveFile(jakarta.servlet.http.HttpServletRequest request)
      throws java.io.IOException {
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
    try (InputStream in = mediaStorageService.getInputStream(imageUrl)) {
      byte[] body = in.readAllBytes();
      String filename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
      String contentType = guessContentType(filename);
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.parseMediaType(contentType));
      headers.setContentLength(body.length);
      return ResponseEntity.ok().headers(headers).body(body);
    } catch (java.io.FileNotFoundException e) {
      return ResponseEntity.notFound().build();
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
