package com.housingplatform.shared.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.housingplatform.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumberNormalizerTest {

  @ParameterizedTest
  @CsvSource({
    "0911223344, +251911223344",
    "911223344, +251911223344",
    "0711223344, +251711223344",
    "251911223344, +251911223344",
    "+251 911 22 33 44, +251911223344",
    "+251-911-223344, +251911223344",
    "00251911223344, +251911223344",
    "+14155552671, +14155552671"
  })
  void normalisesToE164(String raw, String expected) {
    assertThat(PhoneNumberNormalizer.toE164(raw)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "abc", "12345", "+0123456789", "0811223344"})
  void rejectsMalformedNumbers(String raw) {
    assertThatThrownBy(() -> PhoneNumberNormalizer.toE164(raw))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void rejectsNull() {
    assertThatThrownBy(() -> PhoneNumberNormalizer.toE164(null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("required");
  }
}
