package com.housingplatform.media.service;

import com.housingplatform.shared.exception.BusinessException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Saves uploaded media to the server directory and returns a URL path. Only the URL is stored in
 * the database; file data is on disk.
 */
@Service
public class MediaStorageService {

  private static final Logger log = LoggerFactory.getLogger(MediaStorageService.class);
  private static final String UPLOADS_PREFIX = "/api/v1/uploads/";
  private static final String FALLBACK_SUBDIR = "housing-platform-uploads";

  private final Path uploadDir;

  public MediaStorageService(@Value("${app.upload-dir:./uploads}") String uploadDirPath) {
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

  /**
   * Saves the file under a relative path (e.g. "properties/{id}/{filename}.jpg"). Returns the
   * public URL path to use in the database (e.g. "/api/v1/uploads/properties/{id}/{filename}.jpg").
   */
  public String save(MultipartFile file, String relativeSubPath) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException("File is empty");
    }
    String originalName = file.getOriginalFilename();
    String ext =
        originalName != null && originalName.contains(".")
            ? originalName.substring(originalName.lastIndexOf('.'))
            : "";
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
   * Deletes the file at the given URL path if it is under our uploads directory. Safe to call if
   * path is null or not an uploads path.
   */
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

  /**
   * Resolves the on-disk path for an uploads URL. Used for streaming/range responses (video, large
   * files) without buffering the whole file in memory.
   */
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

  /**
   * Returns an input stream to read the file for the given URL path. Caller must close the stream.
   */
  public InputStream getInputStream(String imageUrl) throws IOException {
    return Files.newInputStream(resolveUploadPath(imageUrl));
  }

  public boolean isUploadsUrl(String imageUrl) {
    return imageUrl != null && imageUrl.startsWith(UPLOADS_PREFIX);
  }
}
