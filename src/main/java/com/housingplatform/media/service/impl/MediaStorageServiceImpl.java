package com.housingplatform.media.service.impl;

import com.housingplatform.media.service.MediaStorageService;
import com.housingplatform.shared.exception.BusinessException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaStorageServiceImpl implements MediaStorageService {

  private static final Logger log = LoggerFactory.getLogger(MediaStorageServiceImpl.class);
  private static final String UPLOADS_PREFIX = "/api/v1/uploads/";
  private static final String FALLBACK_SUBDIR = "ethio-build-connect-uploads";

  /**
   * Extensions a stored upload may carry. Uploads are served from the site's own origin (nginx
   * serves this directory directly), so anything a browser would execute or render as a document -
   * .html, .svg, .js, .xml and friends - must never be written here.
   */
  static final Set<String> ALLOWED_EXTENSIONS =
      Set.of(
          ".jpg", ".jpeg", ".png", ".gif", ".webp", ".heic", ".heif", ".avif", ".mp4", ".webm",
          ".mov", ".m4v", ".3gp", ".pdf", ".doc", ".docx");

  /** Fallback extension when the client name has none (or a disallowed one) but a known type. */
  private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE =
      Map.ofEntries(
          Map.entry("image/jpeg", ".jpg"),
          Map.entry("image/png", ".png"),
          Map.entry("image/gif", ".gif"),
          Map.entry("image/webp", ".webp"),
          Map.entry("image/heic", ".heic"),
          Map.entry("image/heif", ".heif"),
          Map.entry("image/avif", ".avif"),
          Map.entry("video/mp4", ".mp4"),
          Map.entry("video/webm", ".webm"),
          Map.entry("video/quicktime", ".mov"),
          Map.entry("video/3gpp", ".3gp"),
          Map.entry("application/pdf", ".pdf"),
          Map.entry("application/msword", ".doc"),
          Map.entry(
              "application/vnd.openxmlformats-officedocument.wordprocessorml.document", ".docx"));

  private final Path uploadDir;

  public MediaStorageServiceImpl(@Value("${app.upload-dir:./uploads}") String uploadDirPath) {
    Path preferred = Path.of(uploadDirPath).toAbsolutePath().normalize();
    Path resolved = preferred;
    try {
      Files.createDirectories(preferred);
    } catch (IOException e) {
      Path fallback =
          Path.of(System.getProperty("java.io.tmpdir", "/tmp")).resolve(FALLBACK_SUBDIR);
      try {
        Files.createDirectories(fallback);
        resolved = fallback;
        log.warn(
            "Upload directory not writable: {}. Using fallback: {} (uploads may not persist across restarts). Set UPLOAD_DIR to a writable path for persistence.",
            preferred,
            resolved);
      } catch (IOException e2) {
        throw new IllegalStateException(
            "Could not create upload directory: "
                + preferred
                + " (nor fallback: "
                + fallback
                + "). Set app.upload-dir (or UPLOAD_DIR) to a writable path.",
            e);
      }
    }
    this.uploadDir = resolved;
  }

  @Override
  public String save(MultipartFile file, String relativeSubPath) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException("File is empty");
    }
    String ext = safeExtension(file.getOriginalFilename(), file.getContentType());
    String filename = UUID.randomUUID() + ext;
    Path targetDir = uploadDir.resolve(relativeSubPath).normalize();
    if (!targetDir.startsWith(uploadDir)) {
      throw new BusinessException("Invalid path");
    }
    try {
      Files.createDirectories(targetDir);
      Path targetFile = targetDir.resolve(filename);
      file.transferTo(targetFile.toFile());
      return UPLOADS_PREFIX + relativeSubPath + "/" + filename;
    } catch (IOException e) {
      throw new BusinessException("Failed to save file: " + e.getMessage());
    }
  }

  /**
   * Picks the stored file's extension from an allowlist, never trusting the client name blindly.
   * Package-private for tests.
   */
  static String safeExtension(String originalName, String contentType) {
    if (originalName != null) {
      int dot = originalName.lastIndexOf('.');
      if (dot >= 0) {
        String ext = originalName.substring(dot).toLowerCase(Locale.ROOT);
        if (ALLOWED_EXTENSIONS.contains(ext)) {
          return ext;
        }
      }
    }
    if (contentType != null) {
      String byType =
          EXTENSION_BY_CONTENT_TYPE.get(contentType.split(";")[0].trim().toLowerCase(Locale.ROOT));
      if (byType != null) {
        return byType;
      }
    }
    throw new BusinessException(
        "Unsupported file type. Allowed: images (JPG, PNG, GIF, WebP, HEIC), videos (MP4, WebM,"
            + " MOV), PDF and Word documents.");
  }

  @Override
  public void deleteByUrl(String imageUrl) {
    if (imageUrl == null || !imageUrl.startsWith(UPLOADS_PREFIX)) {
      return;
    }
    String relative = imageUrl.substring(UPLOADS_PREFIX.length());
    if (relative.isBlank() || relative.contains("..")) {
      return;
    }
    Path file = uploadDir.resolve(relative).normalize();
    if (!file.startsWith(uploadDir)) {
      return;
    }
    try {
      Files.deleteIfExists(file);
    } catch (IOException ignored) {
      // best-effort delete
    }
  }

  @Override
  public Path resolveUploadPath(String imageUrl) throws IOException {
    if (imageUrl == null || !imageUrl.startsWith(UPLOADS_PREFIX)) {
      throw new IllegalArgumentException("Not an uploads URL: " + imageUrl);
    }
    String relative = imageUrl.substring(UPLOADS_PREFIX.length());
    if (relative.contains("..")) {
      throw new IllegalArgumentException("Invalid path");
    }
    Path file = uploadDir.resolve(relative).normalize();
    if (!file.startsWith(uploadDir) || !Files.isRegularFile(file)) {
      throw new FileNotFoundException("File not found: " + imageUrl);
    }
    return file;
  }

  @Override
  public InputStream getInputStream(String imageUrl) throws IOException {
    return Files.newInputStream(resolveUploadPath(imageUrl));
  }

  @Override
  public boolean isUploadsUrl(String imageUrl) {
    return imageUrl != null && imageUrl.startsWith(UPLOADS_PREFIX);
  }
}
