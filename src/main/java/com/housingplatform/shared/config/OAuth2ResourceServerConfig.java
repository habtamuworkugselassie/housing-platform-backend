package com.housingplatform.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.housingplatform.shared.security.RateLimitingFilter;
import com.housingplatform.shared.security.ScopeAuthorizationFilter;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class OAuth2ResourceServerConfig {

  @Value(
      "${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:8080/auth/realms/housing-platform}")
  private String issuerUri;

  @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
  private String jwkSetUri;

  @Value("${jwt.secret:}")
  private String jwtSecret;

  @Value("${jwt.issuer:housing-platform}")
  private String jwtIssuer;

  @Value(
      "${app.cors.allowed-origin-patterns:http://localhost:3000,http://127.0.0.1:3000,http://209.38.204.219*}")
  private String allowedOriginPatterns;

  private final ScopeAuthorizationFilter scopeAuthorizationFilter;

  private RateLimitingFilter rateLimitingFilter;

  public OAuth2ResourceServerConfig(ScopeAuthorizationFilter scopeAuthorizationFilter) {
    this.scopeAuthorizationFilter = scopeAuthorizationFilter;
  }

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  public void setRateLimitingFilter(RateLimitingFilter rateLimitingFilter) {
    this.rateLimitingFilter = rateLimitingFilter;
  }

  @Bean
  @Order(1)
  public SecurityFilterChain publicSecurityFilterChain(HttpSecurity http) throws Exception {
    // Separate filter chain for public endpoints - no OAuth2 resource server
    // This MUST be ordered first (lower order number = higher priority)
    // Note: /api/v1/auth/logout is excluded as it requires authentication
    http.securityMatcher(
            request -> {
              String path = request.getRequestURI();
              // Root endpoint
              if (path.equals("/") || path.isEmpty()) {
                return true;
              }
              // Public auth endpoints
              if (path.equals("/api/v1/auth/register")
                  || path.equals("/api/v1/auth/login")
                  || path.equals("/api/v1/auth/refresh")) {
                return true;
              }
              // Swagger/API docs
              if (path.startsWith("/swagger-ui")
                  || path.equals("/swagger-ui.html")
                  || path.startsWith("/api-docs")
                  || path.startsWith("/v3/api-docs")) {
                return true;
              }
              // Actuator health
              if (path.equals("/actuator/health") || path.equals("/error")) {
                return true;
              }
              // Property image file endpoints (public access for viewing)
              if (path.matches("/api/v1/properties/[^/]+/images/[^/]+/file")) {
                return true;
              }
              // Organization media file endpoints (public access for viewing)
              if (path.matches("/api/v1/organizations/[^/]+/media/[^/]+/file")) {
                return true;
              }
              // Uploaded media files (stored on disk, URL in DB)
              if (path.startsWith("/api/v1/uploads/")) {
                return true;
              }
              // Sponsored organizations for landing page carousel
              if (path.equals("/api/v1/sponsorships/sponsored-organizations")) {
                return true;
              }
              // First property media for organization (sponsor carousel fallback)
              if (path.matches("/api/v1/properties/organization/[^/]+/first-media")) {
                return true;
              }
              // Marketplace organizations list (public by type)
              if (path.startsWith("/api/v1/organizations/marketplace")) {
                return true;
              }
              return false;
            })
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    // Add rate limiting filter only if it's enabled
    if (rateLimitingFilter != null) {
      http.addFilterBefore(rateLimitingFilter, BearerTokenAuthenticationFilter.class);
    }
    return http.build();
  }

  @Bean
  @Order(2)
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    // Main security filter chain - excludes public endpoints via securityMatcher
    // This chain handles authenticated endpoints only
    http.securityMatcher(
            request -> {
              String path = request.getRequestURI();
              // Exclude public endpoints - they're handled by publicSecurityFilterChain
              // But include /api/v1/auth/logout as it requires authentication
              if (path.startsWith("/swagger-ui")
                  || path.startsWith("/api-docs")
                  || path.startsWith("/v3/api-docs")
                  || path.startsWith("/actuator/health")
                  || path.equals("/error")) {
                return false;
              }
              // Exclude public auth endpoints, but include logout
              if (path.startsWith("/api/v1/auth")) {
                return path.equals("/api/v1/auth/logout");
              }
              // Exclude property image file endpoints (public access for viewing)
              if (path.matches("/api/v1/properties/[^/]+/images/[^/]+/file")) {
                return false;
              }
              // Exclude organization media file endpoints (public access for viewing)
              if (path.matches("/api/v1/organizations/[^/]+/media/[^/]+/file")) {
                return false;
              }
              // Exclude uploaded media (disk storage URLs)
              if (path.startsWith("/api/v1/uploads/")) {
                return false;
              }
              // All other /api/** paths go through this chain
              return path.startsWith("/api/");
            })
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth
                    // This chain handles authenticated endpoints

                    // Public property viewing - handled by ScopeAuthorizationFilter with UNSECURED
                    // policy
                    // Property management requires authentication and will be checked by
                    // ScopeAuthorizationFilter

                    // Buyer Portal APIs
                    .requestMatchers("/api/v1/loan-applications/**")
                    .hasAnyAuthority("SCOPE_buyer", "SCOPE_banker", "SCOPE_admin")
                    .requestMatchers("/api/v1/notifications/**")
                    .hasAnyAuthority(
                        "SCOPE_buyer",
                        "SCOPE_banker",
                        "SCOPE_realtor",
                        "SCOPE_supplier",
                        "SCOPE_admin")

                    // Banker Portal APIs
                    .requestMatchers("/api/v1/banks/*/credit-products/**")
                    .hasAnyAuthority("SCOPE_banker", "SCOPE_admin")
                    .requestMatchers("/api/v1/banks/*/financing-offers/**")
                    .hasAnyAuthority("SCOPE_banker", "SCOPE_admin")

                    // Supplier Portal APIs
                    .requestMatchers("/api/v1/materials/**")
                    .hasAnyAuthority("SCOPE_supplier", "SCOPE_admin")
                    .requestMatchers("/api/v1/bills-of-quantities/**")
                    .hasAnyAuthority("SCOPE_supplier", "SCOPE_realtor", "SCOPE_admin")

                    // Real Estate Agent APIs
                    .requestMatchers("/api/v1/real-estate-agents/**")
                    .hasAnyAuthority("SCOPE_realtor", "SCOPE_admin")

                    // Admin Portal APIs
                    .requestMatchers("/api/v1/organizations/*/approve")
                    .hasAuthority("SCOPE_admin")
                    .requestMatchers("/api/v1/organizations/*/reject")
                    .hasAuthority("SCOPE_admin")
                    .requestMatchers("/api/v1/organizations/*/suspend")
                    .hasAuthority("SCOPE_admin")
                    // Secure endpoints that should not be public
                    .requestMatchers("/api/v1/organizations/my-company")
                    .hasAnyAuthority(
                        "SCOPE_admin", "SCOPE_banker", "SCOPE_realtor", "SCOPE_supplier")
                    .requestMatchers("/api/v1/organizations/my-bank")
                    .hasAnyAuthority(
                        "SCOPE_admin", "SCOPE_banker", "SCOPE_realtor", "SCOPE_supplier")
                    // Allow unauthenticated access to GET /api/v1/organizations/{id} only
                    // (UUID-based organization details)
                    .requestMatchers(HttpMethod.GET, "/api/v1/organizations/*")
                    .permitAll()
                    .requestMatchers("/api/v1/organizations/**")
                    .hasAnyAuthority(
                        "SCOPE_admin", "SCOPE_banker", "SCOPE_realtor", "SCOPE_supplier")
                    .requestMatchers("/api/v1/users/**")
                    .hasAnyAuthority(
                        "SCOPE_admin",
                        "SCOPE_buyer",
                        "SCOPE_banker",
                        "SCOPE_realtor",
                        "SCOPE_supplier")

                    // Payment APIs (restricted)
                    .requestMatchers("/api/v1/payments/**")
                    .hasAnyAuthority("SCOPE_banker", "SCOPE_admin")

                    // All other API requests - scope-based access control handled by
                    // ScopeAuthorizationFilter
                    // Allow all API requests to proceed - ScopeAuthorizationFilter will check
                    // UNSECURED annotations
                    .requestMatchers("/api/**")
                    .permitAll()
                    .anyRequest()
                    .permitAll())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(
                        jwt ->
                            jwt.decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                    .accessDeniedHandler(
                        (request, response, accessDeniedException) -> {
                          // For API endpoints, let ScopeAuthorizationFilter handle access control
                          // Don't block here - let the filter chain continue
                          String path = request.getRequestURI();
                          if (path.startsWith("/api/")) {
                            // Allow request to proceed - ScopeAuthorizationFilter will handle it
                            return;
                          }
                          // For other paths, return 403
                          response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        })
                    .bearerTokenResolver(
                        request -> {
                          // Extract bearer token if present, otherwise return null
                          // ScopeAuthorizationFilter will handle UNSECURED endpoints
                          String authHeader = request.getHeader("Authorization");
                          if (authHeader != null && authHeader.startsWith("Bearer ")) {
                            return authHeader.substring(7);
                          }
                          return null; // No token present - let ScopeAuthorizationFilter handle it
                        })
                    .authenticationEntryPoint(
                        (request, response, authException) -> {
                          try {
                            // For endpoints that might be public (UNSECURED), don't fail
                            // immediately
                            // Let ScopeAuthorizationFilter handle it
                            String path = request.getRequestURI();

                            // For API endpoints, always allow requests to proceed
                            // ScopeAuthorizationFilter will check if it's UNSECURED and handle
                            // accordingly
                            if (path.startsWith("/api/")) {
                              // Check if there's actually a token in the request
                              String authHeader = request.getHeader("Authorization");
                              boolean hasToken =
                                  authHeader != null && authHeader.startsWith("Bearer ");

                              // If no token is present, let it proceed to ScopeAuthorizationFilter
                              // If token is present but invalid, still let it proceed (filter will
                              // handle it)
                              // Only block if it's a critical security issue
                              if (!hasToken) {
                                // No token - let ScopeAuthorizationFilter handle UNSECURED
                                // endpoints
                                return;
                              }

                              // Token present but invalid - check if it's a validation error
                              boolean isJwtValidationError =
                                  authException.getCause() instanceof JwtException
                                      || (authException.getMessage() != null
                                          && (authException.getMessage().contains("JWT")
                                              || authException.getMessage().contains("token")));

                              // For API endpoints, let ScopeAuthorizationFilter handle even invalid
                              // tokens
                              // It will check UNSECURED and allow or deny accordingly
                              if (isJwtValidationError) {
                                // Invalid token - still let it proceed, filter will handle
                                return;
                              }
                            }

                            // For other cases or JWT validation errors, return error
                            // Check if it's a JWT-related exception
                            boolean isJwtError =
                                authException instanceof AuthenticationServiceException
                                    || authException.getCause() instanceof JwtException
                                    || authException.getCause() instanceof ExpiredJwtException;

                            // Determine error message based on exception type
                            String errorMessage = "Authentication required";

                            if (isJwtError) {
                              Throwable cause = authException.getCause();
                              if (cause instanceof ExpiredJwtException
                                  || (cause != null
                                      && cause.getMessage() != null
                                      && cause.getMessage().contains("expired"))) {
                                errorMessage = "JWT token has expired. Please refresh your token.";
                              } else if (cause instanceof JwtException
                                  || (authException.getMessage() != null
                                      && authException.getMessage().contains("JWT"))) {
                                errorMessage = "JWT token is invalid.";
                              }
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
                          } catch (IOException e) {
                            // Ignore IOException
                          }
                        }))
        .addFilterBefore(
            new com.housingplatform.shared.security.JwtExceptionHandlerFilter(),
            BearerTokenAuthenticationFilter.class)
        .addFilterBefore(scopeAuthorizationFilter, UsernamePasswordAuthenticationFilter.class);
    // Add rate limiting filter only if it's enabled
    if (rateLimitingFilter != null) {
      http.addFilterBefore(rateLimitingFilter, BearerTokenAuthenticationFilter.class);
    }

    return http.build();
  }

  @Bean
  public JwtDecoder jwtDecoder() {
    // If external OAuth2 server is configured, use it
    if (jwkSetUri != null && !jwkSetUri.isEmpty()) {
      return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    // Otherwise, use our internal JWT decoder (for self-issued tokens)
    // This allows the application to issue its own tokens via /api/v1/auth/login
    return new com.housingplatform.shared.security.InternalJwtDecoder(jwtSecret, jwtIssuer);
  }

  @Bean
  public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
    return new com.housingplatform.shared.security.CustomJwtAuthenticationConverter();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    // Use allowedOriginPatterns instead of allowedOrigins when allowCredentials is true
    // This allows pattern matching while still supporting credentials
    List<String> parsedOriginPatterns = new ArrayList<>();
    for (String originPattern : allowedOriginPatterns.split(",")) {
      String trimmed = originPattern.trim();
      if (!trimmed.isEmpty()) {
        parsedOriginPatterns.add(trimmed);
      }
    }
    configuration.setAllowedOriginPatterns(parsedOriginPatterns);
    configuration.setAllowedMethods(
        Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);
    configuration.setExposedHeaders(List.of("Authorization", "Content-Type"));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
