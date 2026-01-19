package com.housingplatform.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import com.housingplatform.shared.security.ScopeAuthorizationFilter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.authentication.AuthenticationServiceException;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class OAuth2ResourceServerConfig {
    
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:8080/auth/realms/housing-platform}")
    private String issuerUri;
    
    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
    private String jwkSetUri;
    
    @Value("${jwt.secret:your-256-bit-secret-key-change-this-in-production-minimum-32-characters}")
    private String jwtSecret;
    
    @Value("${jwt.issuer:housing-platform}")
    private String jwtIssuer;
    
    private final ScopeAuthorizationFilter scopeAuthorizationFilter;
    
    public OAuth2ResourceServerConfig(ScopeAuthorizationFilter scopeAuthorizationFilter) {
        this.scopeAuthorizationFilter = scopeAuthorizationFilter;
    }
    
    @Bean
    @Order(1)
    public SecurityFilterChain publicSecurityFilterChain(HttpSecurity http) throws Exception {
        // Separate filter chain for public endpoints - no OAuth2 resource server
        // This MUST be ordered first (lower order number = higher priority)
        // Note: /api/v1/auth/logout is excluded as it requires authentication
        http
            .securityMatcher("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
                           "/swagger-ui/**", "/swagger-ui.html", 
                           "/api-docs/**", "/v3/api-docs/**", "/actuator/health", "/error")
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );
        return http.build();
    }
    
    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Main security filter chain - excludes public endpoints via securityMatcher
        // This chain handles authenticated endpoints only
        http
            .securityMatcher(request -> {
                String path = request.getRequestURI();
                // Exclude public endpoints - they're handled by publicSecurityFilterChain
                // But include /api/v1/auth/logout as it requires authentication
                if (path.startsWith("/swagger-ui") ||
                    path.startsWith("/api-docs") ||
                    path.startsWith("/v3/api-docs") ||
                    path.startsWith("/actuator/health") ||
                    path.equals("/error")) {
                    return false;
                }
                // Exclude public auth endpoints, but include logout
                if (path.startsWith("/api/v1/auth")) {
                    return path.equals("/api/v1/auth/logout");
                }
                // All other /api/** paths go through this chain
                return path.startsWith("/api/");
            })
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // This chain handles authenticated endpoints
                
                // Public property viewing - handled by ScopeAuthorizationFilter with UNSECURED policy
                // Property management requires authentication and will be checked by ScopeAuthorizationFilter
                
                // Buyer Portal APIs
                .requestMatchers("/api/v1/loan-applications/**").hasAnyAuthority("SCOPE_buyer", "SCOPE_banker", "SCOPE_admin")
                .requestMatchers("/api/v1/notifications/**").hasAnyAuthority("SCOPE_buyer", "SCOPE_banker", "SCOPE_realtor", "SCOPE_supplier", "SCOPE_admin")
                
                // Banker Portal APIs
                .requestMatchers("/api/v1/banks/*/credit-products/**").hasAnyAuthority("SCOPE_banker", "SCOPE_admin")
                .requestMatchers("/api/v1/banks/*/financing-offers/**").hasAnyAuthority("SCOPE_banker", "SCOPE_admin")
                
                // Supplier Portal APIs
                .requestMatchers("/api/v1/materials/**").hasAnyAuthority("SCOPE_supplier", "SCOPE_admin")
                .requestMatchers("/api/v1/bills-of-quantities/**").hasAnyAuthority("SCOPE_supplier", "SCOPE_realtor", "SCOPE_admin")
                
                // Real Estate Agent APIs
                .requestMatchers("/api/v1/real-estate-agents/**").hasAnyAuthority("SCOPE_realtor", "SCOPE_admin")
                
                // Admin Portal APIs
                .requestMatchers("/api/v1/organizations/*/approve").hasAuthority("SCOPE_admin")
                .requestMatchers("/api/v1/organizations/*/reject").hasAuthority("SCOPE_admin")
                .requestMatchers("/api/v1/organizations/**").hasAnyAuthority("SCOPE_admin", "SCOPE_banker", "SCOPE_realtor", "SCOPE_supplier")
                .requestMatchers("/api/v1/users/**").hasAnyAuthority("SCOPE_admin", "SCOPE_buyer", "SCOPE_banker", "SCOPE_realtor", "SCOPE_supplier")
                
                // Payment APIs (restricted)
                .requestMatchers("/api/v1/payments/**").hasAnyAuthority("SCOPE_banker", "SCOPE_admin")
                
                // All other API requests - scope-based access control handled by ScopeAuthorizationFilter
                .requestMatchers("/api/**").permitAll()
                
                .anyRequest().permitAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
                .bearerTokenResolver(request -> {
                    // For public endpoints, return null to skip JWT processing
                    String path = request.getRequestURI();
                    if (path.startsWith("/api/v1/auth") || 
                        path.startsWith("/swagger-ui") ||
                        path.startsWith("/api-docs") ||
                        path.startsWith("/v3/api-docs") ||
                        path.startsWith("/actuator/health") ||
                        path.equals("/error")) {
                        return null; // Skip JWT processing for public endpoints
                    }
                    // For other endpoints, extract bearer token if present
                    String authHeader = request.getHeader("Authorization");
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        return authHeader.substring(7);
                    }
                    return null; // No token present
                })
                .authenticationEntryPoint((request, response, authException) -> {
                    try {
                        // For public endpoints or when no token is present, don't fail
                        // This allows requests to proceed without authentication
                        String path = request.getRequestURI();
                        if (path.startsWith("/api/v1/auth") || 
                            path.startsWith("/swagger-ui") ||
                            path.startsWith("/api-docs") ||
                            path.startsWith("/v3/api-docs") ||
                            path.startsWith("/actuator/health") ||
                            path.equals("/error")) {
                            // Allow public endpoints to proceed without authentication
                            // Don't write anything, just let it continue
                            return;
                        }
                        
                        // Check if it's a JWT-related exception
                        boolean isJwtError = authException instanceof AuthenticationServiceException ||
                                           authException.getCause() instanceof JwtException ||
                                           authException.getCause() instanceof ExpiredJwtException;
                        
                        // Determine error message based on exception type
                        String errorMessage = "Authentication required";
                        
                        if (isJwtError) {
                            Throwable cause = authException.getCause();
                            if (cause instanceof ExpiredJwtException || 
                                (cause != null && cause.getMessage() != null && cause.getMessage().contains("expired"))) {
                                errorMessage = "JWT token has expired. Please refresh your token.";
                            } else if (cause instanceof JwtException || 
                                      (authException.getMessage() != null && authException.getMessage().contains("JWT"))) {
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
                })
            )
            .addFilterBefore(new com.housingplatform.shared.security.JwtExceptionHandlerFilter(), 
                           BearerTokenAuthenticationFilter.class)
            .addFilterBefore(scopeAuthorizationFilter, UsernamePasswordAuthenticationFilter.class);
        
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
        configuration.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(List.of("Authorization", "Content-Type"));
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
