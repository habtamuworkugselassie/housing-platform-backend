package com.housingplatform.property.api;

import com.housingplatform.property.service.PropertyDocumentService.DocumentFile;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Streams a private document inline, never cached by shared caches. */
public final class DocumentFileResponses {
  private DocumentFileResponses() {}

  public static ResponseEntity<Resource> inline(DocumentFile f) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(f.contentType()))
        .contentLength(f.size())
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline()
                .filename(f.fileName(), java.nio.charset.StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(new PathResource(f.path()));
  }
}
