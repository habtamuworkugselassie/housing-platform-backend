package com.housingplatform.media.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.housingplatform.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

class MediaStorageSafeExtensionTest {

  @Test
  void keepsAllowedExtensionsLowercased() {
    assertThat(MediaStorageServiceImpl.safeExtension("Photo.JPG", "image/jpeg")).isEqualTo(".jpg");
    assertThat(MediaStorageServiceImpl.safeExtension("deed.pdf", "application/pdf"))
        .isEqualTo(".pdf");
    assertThat(MediaStorageServiceImpl.safeExtension("clip.mov", "video/quicktime"))
        .isEqualTo(".mov");
  }

  @Test
  void derivesExtensionFromContentTypeWhenNameHasNone() {
    assertThat(MediaStorageServiceImpl.safeExtension("blob", "image/png")).isEqualTo(".png");
    assertThat(MediaStorageServiceImpl.safeExtension(null, "video/mp4; codecs=avc1"))
        .isEqualTo(".mp4");
  }

  @Test
  void neverStoresBrowserRenderableFiles() {
    for (String name : new String[] {"x.html", "x.svg", "x.js", "x.xhtml", "x.xml", "x.htm"}) {
      // A lying image/png content type must not let the dangerous extension through.
      assertThat(MediaStorageServiceImpl.safeExtension(name, "image/png")).isEqualTo(".png");
      assertThatThrownBy(() -> MediaStorageServiceImpl.safeExtension(name, "text/html"))
          .isInstanceOf(BusinessException.class);
    }
    assertThatThrownBy(() -> MediaStorageServiceImpl.safeExtension("logo.svg", "image/svg+xml"))
        .isInstanceOf(BusinessException.class);
  }
}
