package com.housingplatform.etrade.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.housingplatform.etrade.EtradeProperties;
import com.housingplatform.etrade.EtradeUnavailableException;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * eTrade's registration lookup: {@code GET
 * /api/Registration/GetRegistrationInfoByTin/{tin}/{lang}}.
 *
 * <p>Observed behaviour of the upstream (October 2026):
 *
 * <ul>
 *   <li>200 with a JSON record when the TIN is registered;
 *   <li>204 with no body when it is not (also for malformed TINs);
 *   <li>417 {@code Invalid referer header} when the Referer header is missing or foreign;
 *   <li>the TLS chain lacks its intermediate CA, see {@link EtradeTrustMaterial}.
 * </ul>
 */
@Component
@Slf4j
public class EtradeRestClient implements EtradeClient {

  static final String REGISTRATION_PATH = "/api/Registration/GetRegistrationInfoByTin/{tin}/{lang}";

  private final EtradeProperties properties;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  @Autowired
  public EtradeRestClient(
      EtradeProperties properties,
      RestClient.Builder builder,
      ObjectMapper objectMapper,
      ResourceLoader resourceLoader) {
    this(
        properties,
        builder.requestFactory(requestFactory(properties, resourceLoader)),
        objectMapper);
  }

  /** For tests: bind the builder to a mock server before passing it in. */
  EtradeRestClient(
      EtradeProperties properties, RestClient.Builder builder, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.restClient =
        builder
            .baseUrl(properties.getBaseUrl())
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.REFERER, properties.getReferer())
            .defaultHeader(HttpHeaders.USER_AGENT, properties.getUserAgent())
            .build();
  }

  private static JdkClientHttpRequestFactory requestFactory(
      EtradeProperties properties, ResourceLoader resourceLoader) {
    HttpClient.Builder http =
        HttpClient.newBuilder()
            .connectTimeout(properties.getConnectTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .proxy(ProxySelector.getDefault());
    if (properties.isTrustBundledIntermediate()) {
      SSLContext ssl =
          EtradeTrustMaterial.sslContextTrusting(
              resourceLoader, properties.getIntermediateCertificate());
      http.sslContext(ssl);
    }
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http.build());
    factory.setReadTimeout(properties.getReadTimeout());
    return factory;
  }

  @Override
  public Optional<EtradeRegistrationResponse> findRegistrationByTin(String tin) {
    try {
      return restClient
          .get()
          .uri(REGISTRATION_PATH, tin, properties.getLanguage())
          .exchange((request, response) -> read(tin, response));
    } catch (ResourceAccessException e) {
      log.warn("eTrade lookup for TIN {} could not reach the server: {}", tin, e.getMessage());
      throw new EtradeUnavailableException(
          "eTrade (etrade.gov.et) could not be reached. Please try again later.", e);
    } catch (RestClientException e) {
      log.warn("eTrade lookup for TIN {} failed", tin, e);
      throw new EtradeUnavailableException("eTrade returned an unreadable response", e);
    }
  }

  /**
   * Reads the body ourselves: eTrade's 204 carries no Content-Type, which RestClient's converters
   * refuse ("no suitable HttpMessageConverter ... application/octet-stream"), and a 417 or 5xx body
   * is plain text worth logging.
   */
  private Optional<EtradeRegistrationResponse> read(String tin, ClientHttpResponse response)
      throws IOException {
    HttpStatusCode status = response.getStatusCode();
    byte[] bytes = response.getBody().readAllBytes();
    if (status.value() == 204 || (status.is2xxSuccessful() && bytes.length == 0)) {
      log.debug("eTrade has no registration for TIN {} (HTTP {})", tin, status.value());
      return Optional.empty();
    }
    if (!status.is2xxSuccessful()) {
      String body = new String(bytes, StandardCharsets.UTF_8);
      log.warn(
          "eTrade lookup for TIN {} failed: HTTP {} {}", tin, status.value(), abbreviate(body));
      if (status.value() == 417) {
        throw new EtradeUnavailableException(
            "eTrade rejected the request (invalid Referer header); check etrade.referer");
      }
      throw new EtradeUnavailableException(
          "eTrade answered HTTP " + status.value() + " while looking up the TIN");
    }
    EtradeRegistrationResponse body;
    try {
      body = objectMapper.readValue(bytes, EtradeRegistrationResponse.class);
    } catch (IOException e) {
      log.warn(
          "eTrade lookup for TIN {} returned non-JSON: {}",
          tin,
          abbreviate(new String(bytes, StandardCharsets.UTF_8)));
      throw new EtradeUnavailableException("eTrade returned an unreadable response", e);
    }
    if (body == null || isBlank(body.tin())) {
      return Optional.empty();
    }
    return Optional.of(body);
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private static String abbreviate(String s) {
    if (s == null) {
      return "";
    }
    return s.length() <= 200 ? s : s.substring(0, 200) + "…";
  }
}
