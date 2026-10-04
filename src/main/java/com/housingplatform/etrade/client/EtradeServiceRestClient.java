package com.housingplatform.etrade.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.housingplatform.etrade.EtradeProperties;
import com.housingplatform.etrade.EtradeUnavailableException;
import com.housingplatform.etrade.dto.EtradeBusinessDetails;
import com.housingplatform.shared.exception.BusinessException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP client for the etrade-service: {@code GET /api/v1/businesses/{tin}} with {@code X-API-Key}.
 * The service's status codes map 1:1 onto ours: 400 bad TIN, 404 unknown TIN, 502 eTrade down.
 */
@Component
@Slf4j
public class EtradeServiceRestClient implements EtradeServiceClient {

  static final String HEADER_API_KEY = "X-API-Key";
  static final String BUSINESS_PATH = "/api/v1/businesses/{tin}";

  private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]{1,300})\"");

  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  @Autowired
  public EtradeServiceRestClient(
      EtradeProperties properties, RestClient.Builder builder, ObjectMapper objectMapper) {
    this(properties, builder, objectMapper, requestFactory(properties));
  }

  /** For tests: a builder already bound to a mock server, whose request factory must stay. */
  static EtradeServiceRestClient forTesting(
      EtradeProperties properties, RestClient.Builder builder, ObjectMapper objectMapper) {
    return new EtradeServiceRestClient(properties, builder, objectMapper, null);
  }

  private EtradeServiceRestClient(
      EtradeProperties properties,
      RestClient.Builder builder,
      ObjectMapper objectMapper,
      ClientHttpRequestFactory requestFactory) {
    this.objectMapper = objectMapper;
    RestClient.Builder b =
        builder
            .baseUrl(trimSlash(properties.getServiceUrl()))
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    if (requestFactory != null) {
      b.requestFactory(requestFactory);
    }
    if (properties.getServiceApiKey() != null && !properties.getServiceApiKey().isBlank()) {
      b.defaultHeader(HEADER_API_KEY, properties.getServiceApiKey());
    }
    this.restClient = b.build();
  }

  private static SimpleClientHttpRequestFactory requestFactory(EtradeProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.getConnectTimeout());
    factory.setReadTimeout(properties.getReadTimeout());
    return factory;
  }

  @Override
  public Optional<EtradeBusinessDetails> findBusiness(String tin, boolean includePhotos) {
    try {
      return restClient
          .get()
          .uri(
              uriBuilder ->
                  uriBuilder
                      .path(BUSINESS_PATH)
                      .queryParamIfPresent(
                          "includePhotos", includePhotos ? Optional.of(true) : Optional.empty())
                      .build(tin))
          .exchange((request, response) -> read(tin, response));
    } catch (ResourceAccessException e) {
      log.warn("etrade-service unreachable for TIN {}: {}", tin, e.getMessage());
      throw new EtradeUnavailableException(
          "The eTrade lookup service could not be reached. Please try again later.", e);
    } catch (RestClientException e) {
      log.warn("etrade-service call for TIN {} failed", tin, e);
      throw new EtradeUnavailableException("The eTrade lookup service returned an error", e);
    }
  }

  private Optional<EtradeBusinessDetails> read(String tin, ClientHttpResponse response)
      throws IOException {
    HttpStatusCode status = response.getStatusCode();
    byte[] bytes = response.getBody().readAllBytes();
    if (status.value() == 404) {
      return Optional.empty();
    }
    if (status.value() == 400) {
      String msg = message(bytes);
      throw new BusinessException(msg != null ? msg : "The TIN was rejected by the eTrade service");
    }
    if (status.value() == 401 || status.value() == 403) {
      log.error(
          "etrade-service rejected our API key (HTTP {}); check etrade.service-api-key", status);
      throw new EtradeUnavailableException("The eTrade lookup service is misconfigured (API key)");
    }
    if (!status.is2xxSuccessful()) {
      String msg = message(bytes);
      log.warn("etrade-service answered HTTP {} for TIN {}: {}", status.value(), tin, msg);
      throw new EtradeUnavailableException(
          msg != null ? msg : "The eTrade lookup service answered HTTP " + status.value());
    }
    try {
      EtradeBusinessDetails body = objectMapper.readValue(bytes, EtradeBusinessDetails.class);
      return body == null || body.tin() == null ? Optional.empty() : Optional.of(body);
    } catch (IOException e) {
      log.warn("etrade-service returned an unreadable body for TIN {}", tin, e);
      throw new EtradeUnavailableException(
          "The eTrade lookup service returned an unreadable response", e);
    }
  }

  private static String message(byte[] body) {
    if (body == null || body.length == 0) {
      return null;
    }
    Matcher m = MESSAGE.matcher(new String(body, StandardCharsets.UTF_8));
    return m.find() ? m.group(1) : null;
  }

  private static String trimSlash(String url) {
    return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
