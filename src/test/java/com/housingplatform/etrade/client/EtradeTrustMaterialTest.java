package com.housingplatform.etrade.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.cert.X509Certificate;
import java.util.List;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;

class EtradeTrustMaterialTest {

  @Test
  void bundledIntermediateIsTheGlobalSignCaThatSignsEtradeGovEt() throws Exception {
    List<X509Certificate> certs =
        EtradeTrustMaterial.readPem(
            new ClassPathResource("etrade/globalsign-gcc-r3-ev-tls-ca-2025.pem"));
    assertThat(certs).hasSize(1);
    X509Certificate ca = certs.get(0);
    assertThat(ca.getSubjectX500Principal().getName())
        .contains("CN=GlobalSign GCC R3 EV TLS CA 2025");
    assertThat(ca.getIssuerX500Principal().getName()).contains("CN=GlobalSign");
    assertThat(ca.getBasicConstraints()).as("is a CA certificate").isGreaterThanOrEqualTo(0);
    ca.checkValidity();
  }

  @Test
  void buildsAnSslContextFromTheJdkAnchorsPlusTheBundle() {
    SSLContext ctx =
        EtradeTrustMaterial.sslContextTrusting(
            new DefaultResourceLoader(), "classpath:etrade/globalsign-gcc-r3-ev-tls-ca-2025.pem");
    assertThat(ctx).isNotNull();
    assertThat(ctx.getProtocol()).isEqualTo("TLS");
  }

  @Test
  void aMissingBundleFailsLoudlyAtStartupRatherThanSilentlyTrustingLess() {
    assertThatThrownBy(
            () ->
                EtradeTrustMaterial.sslContextTrusting(
                    new DefaultResourceLoader(), "classpath:etrade/does-not-exist.pem"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does-not-exist.pem");
  }
}
