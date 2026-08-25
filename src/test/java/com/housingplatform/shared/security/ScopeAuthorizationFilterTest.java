package com.housingplatform.shared.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.housingplatform.identity.api.OrganizationAccountController;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The gate that keeps a plain admin out of super-admin endpoints. If this ever regresses, every
 * admin silently regains the ability to issue and reset sponsor-company credentials.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScopeAuthorizationFilterTest {

  @Mock private RequestMappingHandlerMapping handlerMapping;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private FilterChain chain;

  private ScopeAuthorizationFilter filter;

  @BeforeEach
  void setUp() {
    filter = new ScopeAuthorizationFilter(handlerMapping);
    when(request.getRequestURI()).thenReturn("/api/v1/organizations/abc/users");
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  /**
   * Points the filter at a real controller method so the annotations under test are the live ones.
   */
  private void handlerIs(String methodName) throws Exception {
    Method method =
        java.util.Arrays.stream(OrganizationAccountController.class.getMethods())
            .filter(m -> m.getName().equals(methodName))
            .findFirst()
            .orElseThrow();
    HandlerMethod handlerMethod =
        new HandlerMethod(mock(OrganizationAccountController.class), method);
    when(handlerMapping.getHandler(any())).thenReturn(new HandlerExecutionChain(handlerMethod));
  }

  private void authenticateWithScopes(String scopes) {
    Jwt jwt =
        new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            Map.of("alg", "HS256"),
            Map.of("sub", "user-1", "email", "a@b.com", "scope", scopes));
    HousingPlatformJwtAuthenticationToken token =
        new HousingPlatformJwtAuthenticationToken(jwt, List.of());
    token.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(token);
  }

  @Test
  void plainAdminCannotCreateACompanyAccount() throws Exception {
    handlerIs("createAccount");
    authenticateWithScopes("admin");

    assertThrows(AccessDeniedException.class, () -> filter.doFilter(request, response, chain));
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void superAdminCanCreateACompanyAccount() throws Exception {
    handlerIs("createAccount");
    authenticateWithScopes("admin super_admin");

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  @Test
  void plainAdminCannotResetACompanyPassword() throws Exception {
    handlerIs("setPassword");
    authenticateWithScopes("admin");

    assertThrows(AccessDeniedException.class, () -> filter.doFilter(request, response, chain));
  }

  @Test
  void plainAdminMayStillListCompanyAccounts() throws Exception {
    handlerIs("getAccounts");
    authenticateWithScopes("admin");

    filter.doFilter(request, response, chain);

    // Read stays at ADMIN_SECURED so ordinary admins keep their overview.
    verify(chain).doFilter(request, response);
  }

  @Test
  void aRealtorCannotListAnotherCompanysAccounts() throws Exception {
    handlerIs("getAccounts");
    authenticateWithScopes("realtor");

    assertThrows(AccessDeniedException.class, () -> filter.doFilter(request, response, chain));
  }

  @Test
  void aSupplierCannotReachTheSuperAdminEndpoints() throws Exception {
    handlerIs("unlinkAccount");
    authenticateWithScopes("supplier");

    assertThrows(AccessDeniedException.class, () -> filter.doFilter(request, response, chain));
  }

  @Test
  void everySuperAdminPolicyMapsToTheSuperAdminScope() {
    // Guards the enum-to-scope switch: a missing case would fail closed at runtime instead.
    assertEquals(
        AuthPolicyScope.Policy.SUPER_ADMIN_SECURED.name(),
        "SUPER_ADMIN_SECURED",
        "policy renamed without updating the filter mapping");
    assertEquals("super_admin", PortalScope.SUPER_ADMIN);
  }
}
