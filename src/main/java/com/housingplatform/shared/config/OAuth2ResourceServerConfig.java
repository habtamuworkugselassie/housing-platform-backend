package com.housingplatform.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.housingplatform.shared.security.RateLimitingFilter;
import com.housingplatform.shared.security.ScopeAuthorizationFilter;
import com.housingplatform.shared.service.TokenBlacklistService;
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
      "${app.cors.allowed-origin-patterns:http://localhost:3000,http://127.0.0.1:3000,http://209.38.204.219*,https://ethiobuildconnect.et,https://www.ethiobuildconnect.et}")
  private String allowedOriginPatterns;

  private final ScopeAuthorizationFilter scopeAuthorizationFilter;
  private final TokenBlacklistService tokenBlacklistService;

  private RateLimitingFilter rateLimitingFilter;

  public OAuth2ResourceServerConfig(
      ScopeAuthorizationFilter scopeAuthorizationFilter,
      TokenBlacklistService tokenBlacklistService) {
    this.scopeAuthorizationFilter = scopeAuthorizationFilter;
    this.tokenBlacklistService = tokenBlacklistService;
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
              // Logout — intentionally public so the frontend can always call it
              // even with an invalid/expired token
              if (path.equals("/api/v1/auth/logout")) {
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
              // Material supplier subcategory catalog (marketplace filter); GET only
              if (path.equals("/api/v1/supplier-subcategories")
                  && "GET".equalsIgnoreCase(request.getMethod())) {
                return true;
              }
              // Public support chat (optional AI backend; no auth)
              if (path.equals("/api/v1/public/support/chat")
                  && "POST".equalsIgnoreCase(request.getMethod())) {
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
              // Exclude public auth endpoints AND logout (it's in the public chain)
              if (path.startsWith("/api/v1/auth")) {
                return false;
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
                        (request, response, accessDeniedException) ->
                            writeError(
                                request,
                                response,
                                HttpServletResponse.SC_FORBIDDEN,
                                "Forbidden",
                                "You do not have permission to access this resource."))
                    .bearerTokenResolver(
                        request -> {
                          // Absent header means "anonymous", not "malformed":
                          // ScopeAuthorizationFilter
                          // decides whether the target endpoint is UNSECURED.
                          String authHeader = request.getHeader("Authorization");
                          if (authHeader != null && authHeader.startsWith("Bearer ")) {
                            return authHeader.substring(7);
                          }
                          return null;
                        })
                    .authenticationEntryPoint(
                        (request, response, authException) -> {
                          // Previously this returned without writing a status for any /api/ path,
                          // on the assumption that ScopeAuthorizationFilter would answer instead.
                          // It runs earlier in the chain, so nothing did: the response completed
                          // as 200 OK with an empty body and clients read denials as success.
                          Throwable cause = authException.getCause();
                          boolean expired =
                              cause instanceof ExpiredJwtException
                                  || (cause != null
                                      && cause.getMessage() != null
                                      && cause.getMessage().contains("expired"));
                          boolean jwtProblem =
                              expired
                                  || cause instanceof JwtException
                                  || authException instanceof AuthenticationServiceException
                                  || (authException.getMessage() != null
                                      && authException.getMessage().contains("JWT"));

                          String message = "Authentication required.";
                          if (expired) {
                            message = "JWT token has expired. Please refresh your token.";
                          } else if (jwtProblem) {
                            message = "JWT token is invalid.";
                          }
                          writeError(
                              request,
                              response,
                              HttpServletResponse.SC_UNAUTHORIZED,
                              "Authentication Failed",
                              message);
                        }))
        .addFilterBefore(
            new com.housingplatform.shared.security.JwtExceptionHandlerFilter(),
            BearerTokenAuthenticationFilter.class)
        // Must run AFTER authentication. Spring Security orders
        // UsernamePasswordAuthenticationFilter *before* BearerTokenAuthenticationFilter, so the
        // previous addFilterBefore(..., UsernamePasswordAuthenticationFilter.class) placed this
        // upstream of the JWT filter, where the SecurityContext is always empty.
        .addFilterAfter(scopeAuthorizationFilter, BearerTokenAuthenticationFilter.class);
    // Add rate limiting filter only if it's enabled
    if (rateLimitingFilter != null) {
      http.addFilterBefore(rateLimitingFilter, BearerTokenAuthenticationFilter.class);
    }

    return http.build();
  }

  /** Writes the platform's standard error envelope. */
  private static void writeError(
      jakarta.servlet.http.HttpServletRequest request,
      HttpServletResponse response,
      int status,
      String error,
      String message) {
    try {
      response.setStatus(status);
      response.setContentType("application/json");
      Map<String, Object> body = new HashMap<>();
      body.put("timestamp", LocalDateTime.now().toString());
      body.put("status", status);
      body.put("error", error);
      body.put("message", message);
      body.put("path", request.getRequestURI());
      response.getWriter().write(new ObjectMapper().writeValueAsString(body));
    } catch (IOException e) {
      // The client is gone; nothing useful to do.
    }
  }

  @Bean
  public JwtDecoder jwtDecoder() {
    // If external OAuth2 server is configured, use it
    if (jwkSetUri != null && !jwkSetUri.isEmpty()) {
      return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    // Otherwise, use our internal JWT decoder (for self-issued tokens)
    // This allows the application to issue its own tokens via /api/v1/auth/login
    return new com.housingplatform.shared.security.InternalJwtDecoder(
        jwtSecret, jwtIssuer, tokenBlacklistService);
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
