package com.housingplatform.shared.security;

import com.housingplatform.shared.security.annotation.AuthActionScope;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Slf4j
@Component
public class ScopeAuthorizationFilter extends OncePerRequestFilter {

  private static final Set<Class<? extends Annotation>> ANNOTATION_SET =
      Stream.of(AuthPolicyScope.class, AuthActionScope.class)
          .collect(Collectors.toCollection(HashSet::new));

  private final RequestMappingHandlerMapping requestHandlerMapping;

  public ScopeAuthorizationFilter(
      @Lazy @Qualifier("requestMappingHandlerMapping")
          RequestMappingHandlerMapping requestHandlerMapping) {
    this.requestHandlerMapping = requestHandlerMapping;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String path = request.getRequestURI();

    // Skip all /api/v1/auth endpoints (login, register, refresh, logout, etc.)
    // and non-API paths — these are handled by the public security filter chain.
    if (path.startsWith("/api/v1/auth")
        || path.startsWith("/swagger-ui")
        || path.startsWith("/api-docs")
        || path.startsWith("/v3/api-docs")
        || path.startsWith("/actuator/health")
        || path.equals("/error")) {
      filterChain.doFilter(request, response);
      return;
    }

    Set<Annotation> authScopeAnnotations;

    try {
      // Check if handler mapping is ready (it might not be during early initialization)
      if (requestHandlerMapping == null) {
        log.debug("Handler mapping not yet initialized, allowing request: {}", path);
        filterChain.doFilter(request, response);
        return;
      }

      HandlerExecutionChain handlerChain = requestHandlerMapping.getHandler(request);

      if (handlerChain == null) {
        log.debug("Handler not found for path: {}, allowing request", path);
        filterChain.doFilter(request, response);
        return;
      }

      Object handler = handlerChain.getHandler();
      if (!(handler instanceof HandlerMethod)) {
        log.debug("Handler is not a HandlerMethod for path: {}, allowing request", path);
        filterChain.doFilter(request, response);
        return;
      }

      HandlerMethod method = (HandlerMethod) handler;

      Set<Annotation> methodAuthScopeAnnotations =
          AnnotatedElementUtils.getAllMergedAnnotations(method.getMethod(), ANNOTATION_SET);

      Set<Annotation> classAuthScopeAnnotations =
          AnnotatedElementUtils.getAllMergedAnnotations(
              method.getMethod().getDeclaringClass(), ANNOTATION_SET);

      authScopeAnnotations = new HashSet<>(classAuthScopeAnnotations);

      // Override any class annotations with method annotations
      for (Annotation methodAnnotation : methodAuthScopeAnnotations) {
        authScopeAnnotations.removeIf(
            classAnnotation ->
                methodAnnotation.annotationType().equals(classAnnotation.annotationType()));
        authScopeAnnotations.add(methodAnnotation);
      }

    } catch (NullPointerException e) {
      // Handler mapping not fully initialized yet - allow request to proceed
      log.debug("Handler mapping not fully initialized for path: {}, allowing request", path);
      filterChain.doFilter(request, response);
      return;
    } catch (Exception e) {
      // For other exceptions, log but don't block the request during initialization
      // Check if it's a bean resolution issue (multiple handler mappings)
      if (e.getMessage() != null
          && (e.getMessage().contains("No qualifying bean")
              || e.getMessage().contains("expected single matching bean but found"))) {
        log.debug(
            "Handler mapping resolution issue for path: {} - allowing request: {}",
            path,
            e.getMessage());
        filterChain.doFilter(request, response);
        return;
      }

      log.warn(
          "Error resolving endpoint scope annotations for path: {} - {}", path, e.getMessage());
      // During initialization, allow requests to proceed
      if (e.getCause() instanceof NullPointerException
          || e.getMessage() != null && e.getMessage().contains("logger")) {
        log.debug("Allowing request due to initialization issue");
        filterChain.doFilter(request, response);
        return;
      }
      // For other errors, rethrow
      throw new ServletException(
          "Error resolving endpoint scope annotations: " + e.getMessage(), e);
    }

    // Validate that we have required annotations
    if (authScopeAnnotations.isEmpty()) {
      // No annotations - allow if endpoint is not under /api/v1
      if (!path.startsWith("/api/v1")) {
        filterChain.doFilter(request, response);
        return;
      }
      log.error("Missing required scope annotations for endpoint: {}", path);
      throw new AccessDeniedException(
          "Endpoint is missing required authorization annotations: " + path);
    }

    // Check if it's UNSECURED
    AuthPolicyScope.Policy policy = null;
    String action = null;

    for (Annotation annotation : authScopeAnnotations) {
      if (annotation instanceof AuthPolicyScope authPolicyScope) {
        policy = authPolicyScope.value();
      } else if (annotation instanceof AuthActionScope authActionScope) {
        action = authActionScope.value();
      }
    }

    // If UNSECURED, allow access
    if (policy != null && policy == AuthPolicyScope.Policy.UNSECURED) {
      filterChain.doFilter(request, response);
      return;
    }

    // Validate authentication and scopes
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication != null
        && authentication.isAuthenticated()
        && authentication instanceof HousingPlatformJwtAuthenticationToken token) {

      Set<String> requiredScopes = new HashSet<>();

      // Add policy-based scope
      if (policy != null) {
        String policyScope = mapPolicyToScope(policy);
        if (policyScope != null) {
          requiredScopes.add(policyScope);
        }
      }

      // Add action-based scope
      if (action != null && !action.isEmpty()) {
        requiredScopes.add(action);
      }

      // Check if token has required scopes
      Set<String> tokenScopes =
          token.getScopes().stream().map(String::toLowerCase).collect(Collectors.toSet());

      Set<String> requiredScopesLower =
          requiredScopes.stream().map(String::toLowerCase).collect(Collectors.toSet());

      // Check if token has at least one of the required scopes
      boolean hasRequiredScope =
          requiredScopesLower.isEmpty()
              || requiredScopesLower.stream().anyMatch(tokenScopes::contains)
              || token.hasScope("admin"); // Admin has access to everything

      if (!hasRequiredScope) {
        log.warn(
            "Access denied for path: {} - Required scopes: {}, Token scopes: {}",
            path,
            requiredScopes,
            tokenScopes);
        throw new AccessDeniedException(
            String.format(
                "Access denied. Required scopes: %s, but token has: %s",
                requiredScopes, tokenScopes));
      }

      filterChain.doFilter(request, response);

    } else {
      // Not authenticated
      if (policy != null && policy != AuthPolicyScope.Policy.UNSECURED) {
        log.error("Unauthenticated access attempt to secured endpoint: {}", path);
        throw new AccessDeniedException("Authentication required");
      }

      filterChain.doFilter(request, response);
    }
  }

  private String mapPolicyToScope(AuthPolicyScope.Policy policy) {
    return switch (policy) {
      case UNSECURED -> null;
      case AUTHENTICATED -> null; // Any authenticated user
      case BUYER_SECURED -> PortalScope.BUYER;
      case BANKER_SECURED -> PortalScope.BANKER;
      case REALTOR_SECURED -> PortalScope.REALTOR;
      case SUPPLIER_SECURED -> PortalScope.SUPPLIER;
      case ADMIN_SECURED -> PortalScope.ADMIN;
    };
  }
}
