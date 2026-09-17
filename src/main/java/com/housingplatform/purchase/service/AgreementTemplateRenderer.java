package com.housingplatform.purchase.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal, dependency-free template rendering for agreement bodies.
 *
 * <ul>
 *   <li>{@code {{key}}} is replaced by the value for {@code key}; a missing or blank value renders
 *       as an empty string.
 *   <li>{@code {{#key}}…{{/key}}} keeps the block only when {@code key} has a non-blank value.
 *   <li>{@code {{^key}}…{{/key}}} keeps the block only when {@code key} is missing or blank.
 * </ul>
 *
 * Blocks may not nest the same key inside itself; different keys nest freely.
 */
public final class AgreementTemplateRenderer {

  private static final Pattern POSITIVE_BLOCK =
      Pattern.compile("\\{\\{#([\\w.]+)}}(.*?)\\{\\{/\\1}}", Pattern.DOTALL);
  private static final Pattern NEGATIVE_BLOCK =
      Pattern.compile("\\{\\{\\^([\\w.]+)}}(.*?)\\{\\{/\\1}}", Pattern.DOTALL);
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([\\w.]+)}}");

  private AgreementTemplateRenderer() {}

  public static String render(String body, Map<String, String> values) {
    String out = body;
    // Resolve blocks repeatedly so blocks nested in other blocks are handled inside-out.
    for (int i = 0; i < 5; i++) {
      String before = out;
      out = resolveBlocks(out, POSITIVE_BLOCK, values, true);
      out = resolveBlocks(out, NEGATIVE_BLOCK, values, false);
      if (out.equals(before)) {
        break;
      }
    }
    Matcher m = PLACEHOLDER.matcher(out);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      String value = values.get(m.group(1));
      m.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : ""));
    }
    m.appendTail(sb);
    return collapseBlankLines(sb.toString());
  }

  private static String resolveBlocks(
      String text, Pattern pattern, Map<String, String> values, boolean keepWhenPresent) {
    Matcher m = pattern.matcher(text);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      boolean present = isPresent(values.get(m.group(1)));
      String replacement = present == keepWhenPresent ? m.group(2) : "";
      m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  private static boolean isPresent(String value) {
    return value != null && !value.isBlank() && !"false".equalsIgnoreCase(value);
  }

  private static String collapseBlankLines(String text) {
    return text.replaceAll("\\n{3,}", "\n\n").trim() + "\n";
  }

  public static String sha256Hex(String content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
