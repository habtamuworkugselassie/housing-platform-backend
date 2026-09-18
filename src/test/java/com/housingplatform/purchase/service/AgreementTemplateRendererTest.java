package com.housingplatform.purchase.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AgreementTemplateRendererTest {

  @Test
  void replacesPlaceholdersAndBlanksUnknownOnes() {
    String out =
        AgreementTemplateRenderer.render(
            "Hello {{buyer.fullName}} from {{provider.name}}{{missing}}.",
            Map.of("buyer.fullName", "Abebe", "provider.name", "Dream Teams Trading PLC"));
    assertThat(out).isEqualTo("Hello Abebe from Dream Teams Trading PLC.\n");
  }

  @Test
  void positiveBlocksAppearOnlyWhenTheKeyIsPresent() {
    String body = "A{{#financing}} financed {{financing.bankName}}{{/financing}} Z";
    assertThat(
            AgreementTemplateRenderer.render(
                body, Map.of("financing", "true", "financing.bankName", "Awash")))
        .isEqualTo("A financed Awash Z\n");
    assertThat(AgreementTemplateRenderer.render(body, Map.of())).isEqualTo("A Z\n");
    assertThat(AgreementTemplateRenderer.render(body, Map.of("financing", ""))).isEqualTo("A Z\n");
  }

  @Test
  void negativeBlocksAppearOnlyWhenTheKeyIsAbsent() {
    String body = "{{^financing}}cash{{/financing}}{{#financing}}loan{{/financing}}";
    assertThat(AgreementTemplateRenderer.render(body, Map.of())).isEqualTo("cash\n");
    assertThat(AgreementTemplateRenderer.render(body, Map.of("financing", "true")))
        .isEqualTo("loan\n");
  }

  @Test
  void blocksNestWhenTheyUseDifferentKeys() {
    String body = "{{#a}}[{{#b}}b{{/b}}{{^b}}no-b{{/b}}]{{/a}}";
    assertThat(AgreementTemplateRenderer.render(body, Map.of("a", "x", "b", "y")))
        .isEqualTo("[b]\n");
    assertThat(AgreementTemplateRenderer.render(body, Map.of("a", "x"))).isEqualTo("[no-b]\n");
    assertThat(AgreementTemplateRenderer.render(body, Map.of("b", "y"))).isEqualTo("\n");
  }

  @Test
  void collapsesRunsOfBlankLinesLeftByRemovedBlocks() {
    String body = "one\n\n{{#x}}gone\n{{/x}}\n\n\ntwo";
    assertThat(AgreementTemplateRenderer.render(body, Map.of())).isEqualTo("one\n\ntwo\n");
  }

  @Test
  void hashIsStableAndHex() {
    String h1 = AgreementTemplateRenderer.sha256Hex("abc");
    assertThat(h1)
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        .isEqualTo(AgreementTemplateRenderer.sha256Hex("abc"));
    assertThat(AgreementTemplateRenderer.sha256Hex("abd")).isNotEqualTo(h1);
  }
}
