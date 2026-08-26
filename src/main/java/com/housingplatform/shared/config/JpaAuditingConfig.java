package com.housingplatform.shared.config;

import com.housingplatform.shared.security.UserContext;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

/**
 * Supplies {@code created_by} / {@code updated_by} for {@link
 * com.housingplatform.shared.domain.BaseAuditEntity}. {@code @EnableJpaAuditing} was already on the
 * application class but had no {@link AuditorAware} bean, so those columns were always null — which
 * meant nothing recorded which operator issued or reset a sponsor company's credentials.
 */
@Configuration
public class JpaAuditingConfig {

  @Bean
  public AuditorAware<String> auditorAware() {
    return () -> {
      try {
        String email = UserContext.getCurrentUserEmail();
        return Optional.ofNullable(email).filter(e -> !e.isBlank());
      } catch (Exception e) {
        // Unauthenticated writes (migrations, scheduled jobs, public registration) have no auditor.
        return Optional.empty();
      }
    };
  }
}
