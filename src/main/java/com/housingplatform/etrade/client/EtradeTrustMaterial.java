package com.housingplatform.etrade.client;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Builds an {@link SSLContext} that trusts everything the JVM already trusts <em>plus</em> the
 * certificates in a PEM file.
 *
 * <p>etrade.gov.et presents only its leaf certificate; the GlobalSign intermediate that signed it
 * is missing from the handshake, so path building fails with "unable to find valid certification
 * path" on a stock JDK (and "unable to get local issuer certificate" in curl). Trusting the
 * intermediate as an additional anchor lets the chain validate without weakening verification for
 * any other host: the leaf still has to be signed by that exact CA, match the hostname, and be in
 * date.
 */
@Slf4j
final class EtradeTrustMaterial {

  private EtradeTrustMaterial() {}

  static SSLContext sslContextTrusting(ResourceLoader resourceLoader, String pemLocation) {
    try {
      KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
      store.load(null, null);
      int n = 0;
      for (X509Certificate cert : defaultTrustAnchors()) {
        store.setCertificateEntry("jdk-" + (n++), cert);
      }
      for (X509Certificate cert : readPem(resourceLoader.getResource(pemLocation))) {
        store.setCertificateEntry("etrade-" + (n++), cert);
        log.info("eTrade client additionally trusts {}", cert.getSubjectX500Principal());
      }
      TrustManagerFactory tmf =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(store);
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, tmf.getTrustManagers(), null);
      return context;
    } catch (IOException | GeneralSecurityException e) {
      throw new IllegalStateException(
          "Could not build the eTrade TLS trust material from " + pemLocation, e);
    }
  }

  private static List<X509Certificate> defaultTrustAnchors() throws GeneralSecurityException {
    TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init((KeyStore) null);
    List<X509Certificate> anchors = new ArrayList<>();
    for (TrustManager tm : tmf.getTrustManagers()) {
      if (tm instanceof X509TrustManager x509) {
        anchors.addAll(List.of(x509.getAcceptedIssuers()));
      }
    }
    return anchors;
  }

  static List<X509Certificate> readPem(Resource pem) throws IOException, GeneralSecurityException {
    if (!pem.exists()) {
      throw new IOException("certificate file not found: " + pem.getDescription());
    }
    try (InputStream in = pem.getInputStream()) {
      CertificateFactory factory = CertificateFactory.getInstance("X.509");
      List<X509Certificate> out = new ArrayList<>();
      for (Certificate c : factory.generateCertificates(in)) {
        out.add((X509Certificate) c);
      }
      if (out.isEmpty()) {
        throw new IOException("no certificates in " + pem.getDescription());
      }
      return out;
    }
  }
}
