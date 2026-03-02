package com.housingplatform.media.service;

import com.housingplatform.shared.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Saves uploaded media to the server directory and returns a URL path. Only the URL is stored in
 * the database; file data is on disk.
 */
@Service
public class MediaStorageService {

  private static final String UPLOADS_PREFIX = "/api/v1/uploads/";

  private final Path uploadDir;

  public MediaStorageService(@Value("${app.upload-dir:./uploads}") String uploadDirPath) {
    this.uploadDir = Path.of(uploadDirPath).toAbsolutePath().normalize();
    try {
      Files.createDirectories(this.uploadDir);
    } catch (IOException e) {
      throw new IllegalStateException("Could not create upload directory: " + this.uploadDir, e);
    }
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
   * Returns an input stream to read the file for the given URL path. Caller must close the stream.
   */
  public InputStream getInputStream(String imageUrl) throws IOException {
    if (imageUrl == null || !imageUrl.startsWith(UPLOADS_PREFIX)) {
      throw new IllegalArgumentException("Not an uploads URL: " + imageUrl);
    }
    String relative = imageUrl.substring(UPLOADS_PREFIX.length());
    if (relative.contains("..")) {
      throw new IllegalArgumentException("Invalid path");
    }
    Path file = uploadDir.resolve(relative).normalize();
    if (!file.startsWith(uploadDir) || !Files.isRegularFile(file)) {
      throw new java.io.FileNotFoundException("File not found: " + imageUrl);
    }
    return Files.newInputStream(file);
  }

  public boolean isUploadsUrl(String imageUrl) {
    return imageUrl != null && imageUrl.startsWith(UPLOADS_PREFIX);
  }
}
