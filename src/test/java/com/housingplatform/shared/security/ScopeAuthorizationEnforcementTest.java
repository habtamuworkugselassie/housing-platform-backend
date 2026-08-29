package com.housingplatform.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.housingplatform.BaseIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Guards the authorization gate itself.
 *
 * <p>Every assertion here corresponds to a defect that reached production: the filter failed open
 * on an internal error, denials were written as 200 OK, and the filter was ordered ahead of the
 * filter that populates the SecurityContext. None of it was caught, because nothing exercised
 * authorization end to end.
 */
@AutoConfigureMockMvc
class ScopeAuthorizationEnforcementTest extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private FilterChainProxy filterChainProxy;
  @Autowired private JwtTokenProvider jwtTokenProvider;

  private String token(String... scopes) {
    return jwtTokenProvider.generateToken(
        UUID.randomUUID(), "user@example.com", List.of(scopes), List.of("USER"), UUID.randomUUID());
  }

  // ---------------------------------------------------------------- ordering

  @Test
  @DisplayName("the scope filter runs after the filter that authenticates the bearer token")
  void scopeFilterIsOrderedAfterBearerTokenFilter() {
    boolean checked = false;
    for (SecurityFilterChain chain : filterChainProxy.getFilterChains()) {
      List<String> names =
          chain.getFilters().stream().map(f -> f.getClass().getSimpleName()).toList();
      int scope = names.indexOf(ScopeAuthorizationFilter.class.getSimpleName());
      int bearer = names.indexOf(BearerTokenAuthenticationFilter.class.getSimpleName());
      if (scope >= 0 && bearer >= 0) {
        assertThat(scope)
            .as(
                "ScopeAuthorizationFilter reads the SecurityContext, so it must run after "
                    + "BearerTokenAuthenticationFilter populates it. Chain: %s",
                names)
            .isGreaterThan(bearer);
        checked = true;
      }
    }
    assertThat(checked).as("no chain contained both filters — the wiring changed").isTrue();
  }

  // ------------------------------------------------------- denied, not open

  @Test
  @DisplayName("ADMIN_SECURED endpoints reject an unauthenticated caller")
  void adminEndpointsRejectAnonymous() throws Exception {
    for (String path :
        List.of(
            "/api/v1/admin/stats",
            "/api/v1/admin/display-settings",
            "/api/v1/admin/exhibition-interests")) {
      MvcResult result = mockMvc.perform(get(path)).andReturn();
      assertThat(result.getResponse().getStatus())
          .as("%s must not be reachable without a token", path)
          .isIn(401, 403);
    }
  }

  /** POST creates a company account and is SUPER_ADMIN_SECURED; the GET listing is not. */
  private String accountsPath() {
    return "/api/v1/organizations/" + UUID.randomUUID() + "/users";
  }

  @Test
  @DisplayName("SUPER_ADMIN_SECURED endpoints reject an unauthenticated caller")
  void superAdminEndpointsRejectAnonymous() throws Exception {
    MvcResult result = mockMvc.perform(post(accountsPath())).andReturn();
    assertThat(result.getResponse().getStatus()).isIn(401, 403);
  }

  @Test
  @DisplayName("AUTHENTICATED endpoints reject an unauthenticated caller")
  void authenticatedEndpointsRejectAnonymous() throws Exception {
    for (String path :
        List.of("/api/v1/construction-projects", "/api/v1/suppliers", "/api/v1/favorites")) {
      MvcResult result = mockMvc.perform(get(path)).andReturn();
      assertThat(result.getResponse().getStatus())
          .as("%s must not be reachable without a token", path)
          .isIn(401, 403);
    }
  }

  @Test
  @DisplayName("a denial is never reported to the client as success")
  void denialIsNeverA2xx() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/admin/stats")).andReturn();
    assertThat(result.getResponse().getStatus())
        .as("a swallowed denial previously completed as 200 OK with an empty body")
        .isGreaterThanOrEqualTo(400);
    assertThat(result.getResponse().getContentAsString())
        .as("the client needs a parseable reason, not an empty body")
        .contains("\"status\"");
  }

  // ------------------------------------------------------ scope enforcement

  @Test
  @DisplayName("a plain admin token cannot reach a super-admin endpoint")
  void adminCannotReachSuperAdminEndpoint() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post(accountsPath()).header("Authorization", "Bearer " + token(PortalScope.ADMIN)))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(403);
  }

  @Test
  @DisplayName("a super-admin token reaches a super-admin endpoint")
  void superAdminReachesSuperAdminEndpoint() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post(accountsPath())
                    .header(
                        "Authorization",
                        "Bearer " + token(PortalScope.ADMIN, PortalScope.SUPER_ADMIN)))
            .andReturn();
    assertThat(result.getResponse().getStatus())
        .as("super_admin must pass the scope gate")
        .isNotIn(401, 403);
  }

  @Test
  @DisplayName("a realtor token cannot reach an admin endpoint")
  void realtorCannotReachAdminEndpoint() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/admin/stats")
                    .header("Authorization", "Bearer " + token(PortalScope.REALTOR)))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(403);
  }

  /**
   * The construction module is annotated {@code AUTHENTICATED} at class level with an
   * {@code @AuthActionScope} on each method. No token ever carries an action scope, so enforcing
   * one would lock every non-admin user out of 29 endpoints.
   */
  @Test
  @DisplayName("an action scope does not lock out the users the policy admits")
  void actionScopeDoesNotLockOutAuthenticatedUsers() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/construction-projects")
                    .header("Authorization", "Bearer " + token(PortalScope.REALTOR)))
            .andReturn();
    assertThat(result.getResponse().getStatus())
        .as("a realtor must pass the AUTHENTICATED gate on the construction module")
        .isNotIn(401, 403);
  }

  // ------------------------------------------------------------- still open

  @Test
  @DisplayName("UNSECURED endpoints remain public")
  void unsecuredEndpointsStayPublic() throws Exception {
    for (String path :
        List.of("/api/v1/buildings", "/api/v1/supplier-subcategories", "/api/v1/properties")) {
      MvcResult result = mockMvc.perform(get(path)).andReturn();
      assertThat(result.getResponse().getStatus())
          .as("%s is public and must stay reachable without a token", path)
          .isNotIn(401, 403);
    }
  }

  @Test
  @DisplayName(
      "a public endpoint that takes no policy annotation by accident is denied, not served")
  void unannotatedApiEndpointFailsClosed() throws Exception {
    // /api/v1 paths with no handler resolve to 404 rather than being waved through.
    MvcResult result = mockMvc.perform(get("/api/v1/there-is-no-such-endpoint")).andReturn();
    assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(400);
  }
}
