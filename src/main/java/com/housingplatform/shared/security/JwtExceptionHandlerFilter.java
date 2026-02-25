package com.housingplatform.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter to catch JWT exceptions and return proper 401 responses This runs before the
 * BearerTokenAuthenticationFilter to handle exceptions gracefully
 */
public class JwtExceptionHandlerFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      filterChain.doFilter(request, response);
    } catch (AuthenticationServiceException e) {
      // Check if it's a JWT-related exception
      Throwable cause = e.getCause();
      if (cause instanceof JwtException || cause instanceof ExpiredJwtException) {
        handleJwtException(request, response, e, cause);
        return;
      }
      // Re-throw if not a JWT exception
      throw e;
    } catch (Exception e) {
      // Check if it's a JWT exception in the cause chain
      Throwable cause = e.getCause();
      if (cause instanceof JwtException
          || cause instanceof ExpiredJwtException
          || e instanceof JwtException) {
        handleJwtException(request, response, e, cause != null ? cause : e);
        return;
      }
      // Re-throw if not a JWT exception
      throw e;
    }
  }

  private void handleJwtException(
      HttpServletRequest request,
      HttpServletResponse response,
      Exception exception,
      Throwable cause)
      throws IOException {
    String path = request.getRequestURI();

    // Determine error message based on exception type
    String errorMessage = "JWT token is invalid.";
    boolean isExpired =
        cause instanceof ExpiredJwtException
            || (cause != null
                && cause.getMessage() != null
                && cause.getMessage().contains("expired"));

    if (isExpired) {
      errorMessage = "JWT token has expired. Please refresh your token.";
    }

    // Return 401 with proper JSON error response
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json");

    Map<String, Object> errorResponse = new HashMap<>();
    errorResponse.put("timestamp", LocalDateTime.now().toString());
    errorResponse.put("status", HttpServletResponse.SC_UNAUTHORIZED);
    errorResponse.put("error", "Authentication Failed");
    errorResponse.put("message", errorMessage);
    errorResponse.put("path", path);

    ObjectMapper mapper = new ObjectMapper();
    response.getWriter().write(mapper.writeValueAsString(errorResponse));
  }
}
