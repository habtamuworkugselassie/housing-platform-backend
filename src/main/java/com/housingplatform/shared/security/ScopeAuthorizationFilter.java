package com.housingplatform.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.housingplatform.shared.security.annotation.AuthActionScope;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpMediaTypeException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Enforces the {@link AuthPolicyScope} annotation on every controller method under {@code /api/v1}.
 *
 * <p>This filter is the platform's real authorization gate. The {@code authorizeHttpRequests}
 * matchers in {@code OAuth2ResourceServerConfig} cannot replace it: whether an endpoint is public
 * is expressed by {@code @AuthPolicyScope(UNSECURED)} on the handler, which a URL matcher cannot
 * see. That makes two properties non-negotiable here:
 *
 * <ul>
 *   <li><b>It must fail closed.</b> Every error path denies. A previous version caught the
 *       exceptions below and called {@code doFilter}, so any fault silently disabled authorization
 *       for the whole API.
 *   <li><b>It must run after authentication.</b> It reads the {@code SecurityContext}, so it is
 *       registered after {@code BearerTokenAuthenticationFilter}, not before {@code
 *       UsernamePasswordAuthenticationFilter} (which Spring Security orders <em>earlier</em> than
 *       the bearer token filter, not later).
 * </ul>
 *
 * <p>Denials are written directly rather than thrown: {@code ExceptionTranslationFilter} sits
 * downstream of this filter and only catches what the filters after it throw, so an exception
 * raised here would escape the chain as a 500 instead of a 401/403.
 */
@Slf4j
@Component
public class ScopeAuthorizationFilter extends OncePerRequestFilter {

  private static final Set<Class<? extends Annotation>> ANNOTATION_SET =
      Stream.of(AuthPolicyScope.class, AuthActionScope.class)
          .collect(Collectors.toCollection(HashSet::new));

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /**
   * Resolved per request rather than injected directly. Injecting the mapping with {@code @Lazy}
   * yields a CGLIB proxy, and {@code AbstractHandlerMapping.getHandler} is {@code final} — so the
   * call ran against the proxy's own uninitialized fields and threw {@code NullPointerException} on
   * every request. {@link ObjectProvider} defers the lookup without proxying.
   */
  private final ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider;

  public ScopeAuthorizationFilter(
      @Qualifier("requestMappingHandlerMapping")
          ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider) {
    this.handlerMappingProvider = handlerMappingProvider;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String path = request.getRequestURI();

    // Handled by the public security filter chain, which has no resource server attached.
    if (path.startsWith("/api/v1/auth")
        || path.startsWith("/swagger-ui")
        || path.startsWith("/api-docs")
        || path.startsWith("/v3/api-docs")
        || path.startsWith("/actuator/health")
        || path.equals("/error")) {
      filterChain.doFilter(request, response);
      return;
    }

    HandlerMethod handlerMethod;
    try {
      HandlerExecutionChain handlerChain = handlerMappingProvider.getObject().getHandler(request);
      if (handlerChain == null || !(handlerChain.getHandler() instanceof HandlerMethod resolved)) {
        // No controller behind this path — Spring will answer 404, or serve a static resource.
        // There is nothing to authorize.
        filterChain.doFilter(request, response);
        return;
      }
      handlerMethod = resolved;
    } catch (HttpRequestMethodNotSupportedException | HttpMediaTypeException e) {
      // The path exists but the verb or content type does not match any handler. No controller
      // method will run; let Spring produce its 405/415.
      filterChain.doFilter(request, response);
      return;
    } catch (Exception e) {
      log.error("Could not resolve a handler for {} — denying the request", path, e);
      deny(
          request,
          response,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Authorization Check Failed",
          "The authorization check could not be completed.");
      return;
    }

    Set<Annotation> annotations = resolveAnnotations(handlerMethod);

    if (annotations.isEmpty()) {
      if (!path.startsWith("/api/v1")) {
        filterChain.doFilter(request, response);
        return;
      }
      log.error("Endpoint {} declares no @AuthPolicyScope — denying the request", path);
      deny(
          request,
          response,
          HttpServletResponse.SC_FORBIDDEN,
          "Forbidden",
          "This endpoint is not configured for authorization.");
      return;
    }

    AuthPolicyScope.Policy policy = null;
    String action = null;
    for (Annotation annotation : annotations) {
      if (annotation instanceof AuthPolicyScope authPolicyScope) {
        policy = authPolicyScope.value();
      } else if (annotation instanceof AuthActionScope authActionScope) {
        action = authActionScope.value();
      }
    }

    if (policy == AuthPolicyScope.Policy.UNSECURED) {
      filterChain.doFilter(request, response);
      return;
    }

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof HousingPlatformJwtAuthenticationToken token)
        || !authentication.isAuthenticated()) {
      log.warn("Unauthenticated request to secured endpoint {}", path);
      deny(
          request,
          response,
          HttpServletResponse.SC_UNAUTHORIZED,
          "Authentication Failed",
          "Authentication required.");
      return;
    }

    Set<String> tokenScopes =
        token.getScopes().stream().map(String::toLowerCase).collect(Collectors.toSet());
    String requiredScope = policy == null ? null : mapPolicyToScope(policy);

    if (!isAllowed(requiredScope, tokenScopes)) {
      log.warn(
          "Access denied for {} — required scope: {}, action: {}, token scopes: {}",
          path,
          requiredScope,
          action,
          tokenScopes);
      deny(
          request,
          response,
          HttpServletResponse.SC_FORBIDDEN,
          "Forbidden",
          "You do not have permission to access this resource.");
      return;
    }

    filterChain.doFilter(request, response);
  }

  /** Method-level annotations win over class-level ones of the same type. */
  private Set<Annotation> resolveAnnotations(HandlerMethod handlerMethod) {
    Set<Annotation> methodAnnotations =
        AnnotatedElementUtils.getAllMergedAnnotations(handlerMethod.getMethod(), ANNOTATION_SET);
    Set<Annotation> classAnnotations =
        AnnotatedElementUtils.getAllMergedAnnotations(
            handlerMethod.getMethod().getDeclaringClass(), ANNOTATION_SET);

    Set<Annotation> merged = new HashSet<>(classAnnotations);
    for (Annotation methodAnnotation : methodAnnotations) {
      merged.removeIf(
          classAnnotation ->
              methodAnnotation.annotationType().equals(classAnnotation.annotationType()));
      merged.add(methodAnnotation);
    }
    return merged;
  }

  /**
   * The policy decides access. {@link AuthActionScope} is deliberately not enforced: tokens only
   * ever carry the portal scopes issued by {@code AuthenticationServiceImpl}, never a fine-grained
   * action scope, so requiring one would make the endpoint unreachable for every caller except an
   * admin. Enforcing it is a change to make on the day action scopes are actually minted into
   * tokens — until then it is documentation, and the policy is the gate.
   */
  private boolean isAllowed(String requiredScope, Set<String> tokenScopes) {
    // AuthPolicyScope.Policy.AUTHENTICATED maps to no scope: any valid token passes.
    if (requiredScope == null) {
      return true;
    }
    // super_admin is exclusive — the blanket admin bypass must not open these endpoints.
    if (PortalScope.SUPER_ADMIN.equals(requiredScope)) {
      return tokenScopes.contains(PortalScope.SUPER_ADMIN);
    }
    return tokenScopes.contains(requiredScope) || tokenScopes.contains(PortalScope.ADMIN);
  }

  private void deny(
      HttpServletRequest request,
      HttpServletResponse response,
      int status,
      String error,
      String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("timestamp", LocalDateTime.now().toString());
    body.put("status", status);
    body.put("error", error);
    body.put("message", message);
    body.put("path", request.getRequestURI());

    OBJECT_MAPPER.writeValue(response.getWriter(), body);
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
      case SUPER_ADMIN_SECURED -> PortalScope.SUPER_ADMIN;
    };
  }
}
