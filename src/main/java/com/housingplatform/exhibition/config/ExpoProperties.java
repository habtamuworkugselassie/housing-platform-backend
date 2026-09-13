package com.housingplatform.exhibition.config;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * The expo's own facts, as the server needs them.
 *
 * <p>These defaults deliberately match what the site shows — {@code eventDetails.js} and {@code
 * exhibition.hero.dateVenue} in the frontend locale files carry the same dates and venue, and the
 * expo's schema.org markup is held to agreeing with them. A mail that names a different date than
 * the landing page is worse than no mail, so if the dates move, all three move together.
 */
@Component
@ConfigurationProperties(prefix = "app.expo")
@Getter
@Setter
public class ExpoProperties {

  private String name = "Ethio Build Connect Expo";

  private LocalDate startDate = LocalDate.of(2026, 11, 16);

  private LocalDate endDate = LocalDate.of(2026, 11, 18);

  private String venue = "Addis Convention Center";

  private String city = "Addis Ababa";

  /**
   * The zone every "how many days until the expo" decision is made in. Ethiopia does not observe
   * DST, so this is a fixed +03:00 — but the server may well not be in Addis Ababa, and a reminder
   * that thinks it is T-1 in UTC while it is already opening day locally is the bug this prevents.
   */
  private String timezone = "Africa/Addis_Ababa";

  private LifecycleEmails lifecycleEmails = new LifecycleEmails();

  @Getter
  @Setter
  public static class LifecycleEmails {

    /** Master switch. Off means nothing is sent and nothing is logged as suppressed either. */
    private boolean enabled = true;

    /**
     * Ceiling on how many registrants one run of the daily job will mail. A shared SMTP account has
     * a sending quota, and blowing through it gets the domain rate-limited on exactly the day the
     * mail mattered. Anyone not reached is left unrecorded, so the next run picks them up.
     */
    private int maxPerRun = 400;

    /** Reply-to address printed in the mail body so a registrant can reach a person. */
    private String replyTo = "";

    /**
     * When the daily reminder run happens, in {@link ExpoProperties#getTimezone()}. Read by the
     * scheduler straight from the property; declared here so the key is documented in one place.
     */
    private String cron = "0 0 9 * * *";
  }
}
