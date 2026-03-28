package com.housingplatform.media.service.impl;

import com.housingplatform.media.service.MediaStorageService;
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

@Service
public class MediaStorageServiceImpl implements MediaStorageService {

  private static final Logger log = LoggerFactory.getLogger(MediaStorageServiceImpl.class);
  private static final String UPLOADS_PREFIX = "/api/v1/uploads/";
  private static final String FALLBACK_SUBDIR = "ethio-build-connect-uploads";

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
