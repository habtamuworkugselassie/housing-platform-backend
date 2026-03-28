package com.housingplatform.media.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import org.springframework.web.multipart.MultipartFile;

/**
 * Saves uploaded media to the server directory and returns a URL path. Only the URL is stored in
 * the database; file data is on disk.
 */
public interface MediaStorageService {

  /**
   * Saves the file under a relative path (e.g. "properties/{id}/{filename}.jpg"). Returns the
   * public URL path to use in the database (e.g. "/api/v1/uploads/properties/{id}/{filename}.jpg").
   */
  String save(MultipartFile file, String relativeSubPath);

  /**
   * Deletes the file at the given URL path if it is under our uploads directory. Safe to call if
   * path is null or not an uploads path.
   */
  void deleteByUrl(String imageUrl);

  /**
   * Resolves the on-disk path for an uploads URL. Used for streaming/range responses (video, large
   * files) without buffering the whole file in memory.
   */
  Path resolveUploadPath(String imageUrl) throws IOException;

  /**
   * Returns an input stream to read the file for the given URL path. Caller must close the stream.
   */
  InputStream getInputStream(String imageUrl) throws IOException;

  boolean isUploadsUrl(String imageUrl);
}
