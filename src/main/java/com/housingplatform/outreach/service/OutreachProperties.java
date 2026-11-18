package com.housingplatform.outreach.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** app.outreach.* in application.yml; all values come from the environment on the droplet. */
@Component
@ConfigurationProperties(prefix = "app.outreach")
@Getter
@Setter
public class OutreachProperties {
  /** Master switch for the scheduler. Admin screens work either way. */
  private boolean enabled = false;

  private int dailyCap = 30;
  private int hourlyCap = 8;

  /** Local hours (inclusive start, exclusive end) in which emails go out, weekdays only. */
  private int sendWindowStart = 9;

  private int sendWindowEnd = 17;
  private String timezone = "Africa/Addis_Ababa";

  /** Business days between the first email and the single follow-up. */
  private int followUpAfterDays = 5;

  private String senderName = "";
  private String senderTitle = "";
  private String senderPhone = "";

  /** The mailbox emails are sent from and replies land in (the Gmail account authorised). */
  private String senderEmail = "";

  private String companyLine = "Dream Teams Trading PLC, Addis Ababa";

  private String siteUrl = "https://ethiobuildconnect.et";

  private Gmail gmail = new Gmail();

  @Getter
  @Setter
  public static class Gmail {
    private String clientId = "";
    private String clientSecret = "";
    private String refreshToken = "";
  }

  public boolean gmailConfigured() {
    return !gmail.clientId.isBlank()
        && !gmail.clientSecret.isBlank()
        && !gmail.refreshToken.isBlank()
        && !senderEmail.isBlank();
  }
}
