package com.housingplatform.media.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UploadsControllerPrivateTest {

  @Test
  void privateFilesAreRecognisedHoweverThePathIsSpelled() {
    assertThat(UploadsController.isPrivate("private/property-documents/p/x.pdf")).isTrue();
    assertThat(UploadsController.isPrivate("./private/property-documents/p/x.pdf")).isTrue();
    assertThat(UploadsController.isPrivate("properties/../private/property-documents/x.pdf"))
        .isTrue();
    assertThat(UploadsController.isPrivate("properties/abc/photo.jpg")).isFalse();
    assertThat(UploadsController.isPrivate("properties/private-villa.jpg")).isFalse();
  }
}
