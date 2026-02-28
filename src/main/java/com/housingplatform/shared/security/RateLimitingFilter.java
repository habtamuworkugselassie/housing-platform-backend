package com.housingplatform.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "rate.limit.auth.enabled",
    havingValue = "true",
    matchIfMissing = false)
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

  private final RedisTemplate<String, String> redisTemplate;

  @Value("${rate.limit.auth.enabled:true}")
  private boolean rateLimitEnabled;

  @Value("${rate.limit.auth.max-requests:5}")
  private int maxRequests;

  @Value("${rate.limit.auth.window-seconds:60}")
  private int windowSeconds;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    if (!rateLimitEnabled) {
      filterChain.doFilter(request, response);
      return;
    }

    String path = request.getRequestURI();

    // Apply rate limiting only to authentication endpoints
    if (path.equals("/api/v1/auth/login")
        || path.equals("/api/v1/auth/register")
        || path.equals("/api/v1/auth/forgot-password")) {
      String clientIdentifier = getClientIdentifier(request);
      String rateLimitKey = "rate_limit:auth:" + clientIdentifier;

      if (isRateLimited(rateLimitKey)) {
        log.warn("Rate limit exceeded for client: {} on path: {}", clientIdentifier, path);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response
            .getWriter()
            .write(
                String.format(
                    "{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Maximum %d requests per %d seconds. Please try again later.\",\"retryAfter\":%d}",
                    maxRequests, windowSeconds, windowSeconds));
        return;
      }
    }

    filterChain.doFilter(request, response);
  }

  private String getClientIdentifier(HttpServletRequest request) {
    // Use IP address as identifier
    String ipAddress = request.getRemoteAddr();

    // Handle X-Forwarded-For header for proxied requests
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
      ipAddress = xForwardedFor.split(",")[0].trim();
    }

    return ipAddress;
  }

  private boolean isRateLimited(String key) {
    try {
      ValueOperations<String, String> ops = redisTemplate.opsForValue();
      String currentCount = ops.get(key);

      if (currentCount == null) {
        // First request in the window
        ops.set(key, "1", Duration.ofSeconds(windowSeconds));
        return false;
      }

      int count = Integer.parseInt(currentCount);
      if (count >= maxRequests) {
        return true;
      }

      // Increment counter
      ops.increment(key);
      // Reset expiration
      redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
      return false;
    } catch (Exception e) {
      log.error("Error checking rate limit for key: {}", key, e);
      // On error, allow the request (fail open)
      return false;
    }
  }
}
