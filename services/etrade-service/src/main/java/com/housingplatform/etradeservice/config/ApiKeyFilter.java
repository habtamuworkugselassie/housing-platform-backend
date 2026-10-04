package com.housingplatform.etradeservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Service-to-service authentication: every {@code /api/**} request must carry one of the configured
 * keys in {@code X-API-Key}. Health, docs and everything else stay open so orchestrators can probe
 * the service. With no keys configured the filter is a no-op.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-API-Key";

  private final EtradeProperties properties;

  public ApiKeyFilter(EtradeProperties properties) {
    this.properties = properties;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/") || properties.getApiKeys().isEmpty();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String presented = request.getHeader(HEADER);
    if (presented != null && matchesAny(presented)) {
      chain.doFilter(request, response);
      return;
    }
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response
        .getWriter()
        .write(
            "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Missing or invalid "
                + HEADER
                + " header\"}");
  }

  private boolean matchesAny(String presented) {
    byte[] given = presented.getBytes(StandardCharsets.UTF_8);
    for (String key : properties.getApiKeys()) {
      if (key != null
          && !key.isBlank()
          && MessageDigest.isEqual(given, key.getBytes(StandardCharsets.UTF_8))) {
        return true;
      }
    }
    return false;
  }
}
