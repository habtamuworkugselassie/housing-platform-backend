package com.housingplatform.exhibition.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.housingplatform.exhibition.domain.LiveBroadcast;
import com.housingplatform.exhibition.domain.LiveBroadcast.BroadcasterRole;
import com.housingplatform.shared.exception.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class LiveBroadcastManageKeyTest {

  // Only the static/no-dependency parts are exercised; the collaborators are never touched.
  private final LiveBroadcastServiceImpl service =
      new LiveBroadcastServiceImpl(null, null, null, null, null, null, null, null, null);

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private static LiveBroadcast visitorStream(String key) {
    LiveBroadcast b = new LiveBroadcast();
    b.setBroadcasterRole(BroadcasterRole.VISITOR);
    b.setManageKeyHash(key == null ? null : LiveBroadcastServiceImpl.sha256Hex(key));
    return b;
  }

  private static void signIn(UUID userId, String scope) {
    Jwt jwt =
        new Jwt(
            "t",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "none"),
            Map.of("sub", userId.toString(), "scope", scope));
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
  }

  @Test
  void keysAreRandomAndUrlSafe() {
    String a = LiveBroadcastServiceImpl.newManageKey();
    String b = LiveBroadcastServiceImpl.newManageKey();
    assertThat(a).isNotEqualTo(b).matches("[A-Za-z0-9_-]{43}");
  }

  @Test
  void visitorStreamNeedsItsKey() {
    String key = LiveBroadcastServiceImpl.newManageKey();
    LiveBroadcast b = visitorStream(key);
    assertThatCode(() -> service.assertBroadcaster(b, key, true, "no")).doesNotThrowAnyException();
    assertThatThrownBy(() -> service.assertBroadcaster(b, null, true, "no"))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> service.assertBroadcaster(b, "guess", true, "no"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void legacyVisitorStreamWithoutKeyStaysOpen() {
    assertThatCode(() -> service.assertBroadcaster(visitorStream(null), null, false, "no"))
        .doesNotThrowAnyException();
  }

  @Test
  void staffMayModerateButNotPublishSomeoneElsesStream() {
    LiveBroadcast b = visitorStream(LiveBroadcastServiceImpl.newManageKey());
    signIn(UUID.randomUUID(), "admin");
    assertThatCode(() -> service.assertBroadcaster(b, null, true, "no")).doesNotThrowAnyException();
    assertThatThrownBy(() -> service.assertBroadcaster(b, null, false, "no"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void exhibitorStreamBelongsToItsAccount() {
    UUID owner = UUID.randomUUID();
    LiveBroadcast b = new LiveBroadcast();
    b.setBroadcasterRole(BroadcasterRole.EXHIBITOR);
    b.setBroadcasterUserId(owner);

    signIn(owner, "buyer");
    assertThatCode(() -> service.assertBroadcaster(b, null, false, "no"))
        .doesNotThrowAnyException();

    signIn(UUID.randomUUID(), "buyer");
    assertThatThrownBy(() -> service.assertBroadcaster(b, null, false, "no"))
        .isInstanceOf(BusinessException.class);
  }
}
